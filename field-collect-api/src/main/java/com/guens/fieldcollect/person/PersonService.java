package com.guens.fieldcollect.person;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guens.fieldcollect.application.FieldApplication;
import com.guens.fieldcollect.application.FieldApplicationRepository;
import com.guens.fieldcollect.common.crypto.CryptoService;
import com.guens.fieldcollect.common.exception.ApiException;
import com.guens.fieldcollect.geo.City;
import com.guens.fieldcollect.geo.CityRepository;
import com.guens.fieldcollect.person.PersonDtos.*;
import com.guens.fieldcollect.security.AuthUser;
import com.guens.fieldcollect.user.UserAssignmentId;
import com.guens.fieldcollect.user.UserAssignmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional
public class PersonService {

    private static final String RECEIVED = "RECEIVED";

    private final PersonRepository persons;
    private final UserAssignmentRepository assignments;
    private final FieldApplicationRepository apps;
    private final CityRepository cities;
    private final CryptoService crypto;
    private final DocumentStorage storage;
    private final ObjectMapper mapper;

    public PersonService(PersonRepository persons, UserAssignmentRepository assignments,
                         FieldApplicationRepository apps, CityRepository cities, CryptoService crypto,
                         DocumentStorage storage, ObjectMapper mapper) {
        this.persons = persons;
        this.assignments = assignments;
        this.apps = apps;
        this.cities = cities;
        this.crypto = crypto;
        this.storage = storage;
        this.mapper = mapper;
    }

    public SubmitResult submit(PersonRequest req, AuthUser agent) {
        String uuid = req.clientUuid().toString();

        // Idempotence : un renvoi du même enregistrement (réseau instable) ne crée rien de nouveau.
        var existing = persons.findByClientUuid(uuid);
        if (existing.isPresent()) {
            Person p = existing.get();
            if (!p.getAgentId().equals(agent.id())) {
                throw new ApiException(HttpStatus.CONFLICT, "CLIENT_UUID_CONFLICT", "Identifiant déjà utilisé.");
            }
            return new SubmitResult(new SubmitResponse(p.getId(), uuid, RECEIVED), false);
        }

        validateScope(req, agent);
        byte[] signature = decodeSignature(req.signature());

        boolean hasNumber = req.documentNumber() != null && !req.documentNumber().isBlank();
        if (hasNumber && req.documentType() == null) {
            throw bad("DOCUMENT_TYPE_REQUIRED", "Le type de pièce est requis avec un numéro de pièce.");
        }
        if (req.captureMode() == CaptureMode.SCAN && (!hasNumber || req.documentType() == null)) {
            throw bad("DOCUMENT_REQUIRED", "Un scan doit fournir le type et le numéro de la pièce.");
        }

        Person p = new Person();
        p.setClientUuid(uuid);
        p.setApplicationId(req.applicationId());
        p.setCountryId(req.countryId());
        p.setCityId(req.cityId());
        p.setAgentId(agent.id());
        p.setDeviceId(agent.deviceId());
        p.setCaptureMode(req.captureMode());
        p.setLastName(req.lastName().trim());
        p.setFirstName(req.firstName().trim());
        p.setGender(req.gender());
        p.setBirthDate(req.birthDate());
        p.setBirthPlace(req.birthPlace());
        p.setNationality(req.nationality());
        p.setPhone(req.phone());
        p.setEmail(req.email());
        p.setAddress(req.address());
        p.setDocumentType(req.documentType());
        p.setDocumentIssueDate(req.documentIssueDate());
        p.setDocumentExpiryDate(req.documentExpiryDate());
        p.setOcrRawText(req.ocrRawText());
        p.setOcrConfidence(req.ocrConfidence());
        p.setCapturedAt(req.capturedAt());
        p.setExtraData(toJson(req));

        if (hasNumber) {
            String normalized = req.documentNumber().replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
            String hash = crypto.hmac(normalized);
            if (persons.existsByApplicationIdAndDocumentTypeAndDocumentNumberHash(
                    req.applicationId(), req.documentType(), hash)) {
                throw new ApiException(HttpStatus.CONFLICT, "PERSON_ALREADY_REGISTERED",
                        "Cette pièce est déjà enregistrée pour cette application.");
            }
            p.setDocumentNumberHash(hash);
            p.setDocumentNumberEnc(crypto.encryptString(normalized));
        }
        p.setSignaturePath(storage.storeSignature(signature));
        persons.save(p);
        return new SubmitResult(new SubmitResponse(p.getId(), uuid, RECEIVED), true);
    }

    public DocumentResponse attachDocument(String clientUuid, byte[] image, String extension, AuthUser agent) {
        // 404 aussi quand l'enregistrement appartient à un autre agent : on ne révèle rien.
        Person p = persons.findByClientUuid(clientUuid)
                .filter(x -> x.getAgentId().equals(agent.id()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Enregistrement introuvable."));
        if (p.getDocumentImagePath() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_ALREADY_UPLOADED", "Image déjà envoyée.");
        }
        p.setDocumentImagePath(storage.store(image, extension));
        return new DocumentResponse(RECEIVED);
    }

    private static byte[] decodeSignature(String base64) {
        byte[] b;
        try {
            b = java.util.Base64.getMimeDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw bad("INVALID_SIGNATURE", "Signature illisible.");
        }
        boolean png = b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        if (!png) throw bad("INVALID_SIGNATURE", "La signature doit être une image PNG.");
        return b;
    }

    private void validateScope(PersonRequest req, AuthUser agent) {
        var id = new UserAssignmentId(agent.id(), req.applicationId(), req.cityId());
        if (!assignments.existsById(id)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_ASSIGNED",
                    "Vous n'êtes pas affecté à cette application dans cette ville.");
        }
        FieldApplication app = apps.findById(req.applicationId())
                .orElseThrow(() -> bad("UNKNOWN_APPLICATION", "Application inconnue."));
        City city = cities.findById(req.cityId()).orElseThrow(() -> bad("UNKNOWN_CITY", "Ville inconnue."));
        if (!city.getCountry().getId().equals(req.countryId())) {
            throw bad("CITY_COUNTRY_MISMATCH", "La ville n'appartient pas au pays indiqué.");
        }
        if (!app.isActive() || !city.isActive() || !city.getCountry().isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INACTIVE_SCOPE",
                    "Cette application, ville ou ce pays est désactivé.");
        }
    }

    private String toJson(PersonRequest req) {
        if (req.extraData() == null || req.extraData().isNull()) return null;
        try {
            return mapper.writeValueAsString(req.extraData());
        } catch (JsonProcessingException e) {
            throw bad("INVALID_EXTRA_DATA", "extraData invalide.");
        }
    }

    private static ApiException bad(String code, String msg) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, msg);
    }
}
