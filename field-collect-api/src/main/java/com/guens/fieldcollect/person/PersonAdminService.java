package com.guens.fieldcollect.person;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guens.fieldcollect.application.FieldApplication;
import com.guens.fieldcollect.application.FieldApplicationRepository;
import com.guens.fieldcollect.audit.AuditService;
import com.guens.fieldcollect.common.crypto.CryptoService;
import com.guens.fieldcollect.common.dto.PageResponse;
import com.guens.fieldcollect.common.exception.ApiException;
import com.guens.fieldcollect.common.exception.NotFound;
import com.guens.fieldcollect.geo.City;
import com.guens.fieldcollect.geo.CityRepository;
import com.guens.fieldcollect.geo.Country;
import com.guens.fieldcollect.geo.CountryRepository;
import com.guens.fieldcollect.person.PersonAdminDtos.*;
import com.guens.fieldcollect.user.User;
import com.guens.fieldcollect.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PersonAdminService {

    private static final int EXPORT_PAGE = 500;

    private final PersonRepository persons;
    private final FieldApplicationRepository apps;
    private final CountryRepository countries;
    private final CityRepository cities;
    private final UserRepository users;
    private final CryptoService crypto;
    private final DocumentStorage storage;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final EntityManager em;

    public PersonAdminService(PersonRepository persons, FieldApplicationRepository apps, CountryRepository countries,
                              CityRepository cities, UserRepository users, CryptoService crypto,
                              DocumentStorage storage, AuditService audit, ObjectMapper mapper, EntityManager em) {
        this.persons = persons;
        this.apps = apps;
        this.countries = countries;
        this.cities = cities;
        this.users = users;
        this.crypto = crypto;
        this.storage = storage;
        this.audit = audit;
        this.mapper = mapper;
        this.em = em;
    }

    /** Résolution des libellés par lot (évite un N+1). */
    private record Names(Map<Long, String> apps, Map<Long, String> countries, Map<Long, String> cities,
                         Map<Long, String> agents) {}

    private Names namesFor(Collection<Person> list) {
        return new Names(
                apps.findAllById(ids(list, Person::getApplicationId)).stream()
                        .collect(Collectors.toMap(FieldApplication::getId, FieldApplication::getName)),
                countries.findAllById(ids(list, Person::getCountryId)).stream()
                        .collect(Collectors.toMap(Country::getId, Country::getName)),
                cities.findAllById(ids(list, Person::getCityId)).stream()
                        .collect(Collectors.toMap(City::getId, City::getName)),
                users.findAllById(ids(list, Person::getAgentId)).stream()
                        .collect(Collectors.toMap(User::getId, User::getFullName)));
    }

    private static Set<Long> ids(Collection<Person> list, java.util.function.Function<Person, Long> f) {
        return list.stream().map(f).collect(Collectors.toSet());
    }

    public PageResponse<PersonSummary> search(PersonFilter filter, int page, int size) {
        Page<Person> result = persons.findAll(filter.toSpec(), PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        Names n = namesFor(result.getContent());
        return PageResponse.of(result, p -> summary(p, n));
    }

    @Transactional
    public PersonDetail detail(Long id, Long actorId) {
        Person p = find(id);
        Names n = namesFor(List.of(p));
        audit.log(actorId, "PERSON_VIEWED", "Person", id);
        return new PersonDetail(p.getId(), p.getClientUuid(),
                p.getApplicationId(), n.apps().get(p.getApplicationId()),
                p.getCountryId(), n.countries().get(p.getCountryId()),
                p.getCityId(), n.cities().get(p.getCityId()),
                p.getAgentId(), n.agents().get(p.getAgentId()), p.getDeviceId(), p.getCaptureMode(),
                p.getLastName(), p.getFirstName(), p.getGender(), p.getBirthDate(), p.getBirthPlace(),
                p.getNationality(), p.getPhone(), p.getEmail(), p.getAddress(),
                p.getDocumentType(), p.getDocumentNumberEnc() == null ? null : crypto.decryptString(p.getDocumentNumberEnc()),
                p.getDocumentIssueDate(), p.getDocumentExpiryDate(), p.getDocumentImagePath() != null,
                p.getSignaturePath() != null, p.getOcrConfidence(), p.getOcrRawText(), json(p.getExtraData()),
                p.getCapturedAt(), p.getCreatedAt());
    }

    @Transactional
    public DocumentImage documentImage(Long id, Long actorId) {
        Person p = find(id);
        if (p.getDocumentImagePath() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NO_DOCUMENT", "Aucune image pour cet enregistrement.");
        }
        audit.log(actorId, "PERSON_DOCUMENT_VIEWED", "Person", id);
        String type = p.getDocumentImagePath().contains(".png.") ? "image/png" : "image/jpeg";
        return new DocumentImage(storage.load(p.getDocumentImagePath()), type);
    }

    @Transactional
    public DocumentImage signatureImage(Long id, Long actorId) {
        Person p = find(id);
        if (p.getSignaturePath() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NO_SIGNATURE", "Aucune signature pour cet enregistrement.");
        }
        audit.log(actorId, "PERSON_SIGNATURE_VIEWED", "Person", id);
        return new DocumentImage(storage.load(p.getSignaturePath()), "image/png");
    }

    /** Export CSV par pages ; le numéro de pièce n'est jamais exporté en clair (masqué). */
    @Transactional
    public void exportCsv(PersonFilter filter, Writer out, Long actorId) throws IOException {
        out.write('﻿'); // BOM : Excel lit correctement l'UTF-8
        csvRow(out, List.of("id", "last_name", "first_name", "gender", "birth_date", "nationality", "phone",
                "document_type", "document_number_masked", "application", "country", "city", "agent",
                "capture_mode", "created_at"));
        long total = 0;
        for (int page = 0; ; page++) {
            Page<Person> chunk = persons.findAll(filter.toSpec(),
                    PageRequest.of(page, EXPORT_PAGE, Sort.by(Sort.Direction.ASC, "id")));
            Names n = namesFor(chunk.getContent());
            for (Person p : chunk.getContent()) {
                csvRow(out, Arrays.asList(String.valueOf(p.getId()), p.getLastName(), p.getFirstName(),
                        str(p.getGender()), str(p.getBirthDate()), p.getNationality(), p.getPhone(),
                        str(p.getDocumentType()), masked(p),
                        n.apps().get(p.getApplicationId()), n.countries().get(p.getCountryId()),
                        n.cities().get(p.getCityId()), n.agents().get(p.getAgentId()),
                        str(p.getCaptureMode()), str(p.getCreatedAt())));
                total++;
            }
            em.clear();
            if (!chunk.hasNext()) break;
        }
        audit.log(actorId, "PERSONS_EXPORTED", "Person", total);
    }

    private Person find(Long id) {
        return persons.findById(id).orElseThrow(() -> NotFound.of("Enregistrement", id));
    }

    private PersonSummary summary(Person p, Names n) {
        return new PersonSummary(p.getId(), p.getLastName(), p.getFirstName(), p.getBirthDate(),
                p.getDocumentType(), masked(p),
                p.getApplicationId(), n.apps().get(p.getApplicationId()),
                p.getCountryId(), n.countries().get(p.getCountryId()),
                p.getCityId(), n.cities().get(p.getCityId()),
                p.getAgentId(), n.agents().get(p.getAgentId()), p.getCaptureMode(),
                p.getDocumentImagePath() != null, p.getCreatedAt());
    }

    private String masked(Person p) {
        if (p.getDocumentNumberEnc() == null) return null;
        String number = crypto.decryptString(p.getDocumentNumberEnc());
        return number.length() <= 4 ? "****" : "****" + number.substring(number.length() - 4);
    }

    private JsonNode json(String raw) {
        if (raw == null) return null;
        try {
            return mapper.readTree(raw);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static void csvRow(Writer out, List<String> cells) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(csvCell(cells.get(i)));
        }
        out.write(sb.append("\r\n").toString());
    }

    /** Échappement CSV + neutralisation de l'injection de formules (=, +, -, @) à l'ouverture dans Excel. */
    static String csvCell(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
