package com.guens.fieldcollect.mobile;

import com.guens.fieldcollect.common.exception.ApiException;
import com.guens.fieldcollect.geo.GeoDtos.CityResponse;
import com.guens.fieldcollect.geo.GeoDtos.CountryResponse;
import com.guens.fieldcollect.mobile.MobileReferenceService.MobileApplication;
import com.guens.fieldcollect.person.PersonDtos.*;
import com.guens.fieldcollect.stats.StatsDtos.DayCount;
import com.guens.fieldcollect.person.PersonService;
import com.guens.fieldcollect.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * API de l'application Flutter. Volontairement write-only pour les personnes :
 * aucune route de lecture/modification des enregistrements n'est exposée à l'agent.
 */
@RestController
@RequestMapping("/api/v1/mobile")
public class MobileController {

    private final MobileReferenceService reference;
    private final PersonService persons;
    private final AgentStatsService agentStats;

    public MobileController(MobileReferenceService reference, PersonService persons,
                            AgentStatsService agentStats) {
        this.reference = reference;
        this.persons = persons;
        this.agentStats = agentStats;
    }

    /** Statistiques de l'agent connecté uniquement : compteurs agrégés, aucune donnée de personne. */
    @GetMapping("/stats")
    public AgentStatsService.Summary myStats(@AuthenticationPrincipal AuthUser me) {
        return agentStats.summary(me.id());
    }

    @GetMapping("/stats/daily")
    public List<DayCount> myDaily(@RequestParam(defaultValue = "30") int days, @AuthenticationPrincipal AuthUser me) {
        return agentStats.daily(me.id(), days);
    }

    @GetMapping("/stats/day")
    public AgentStatsService.DayDetail myDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal AuthUser me) {
        return agentStats.day(me.id(), date);
    }

    @GetMapping("/countries")
    public List<CountryResponse> countries(@AuthenticationPrincipal AuthUser me) {
        return reference.countries(me.id());
    }

    @GetMapping("/countries/{countryId}/cities")
    public List<CityResponse> cities(@PathVariable Long countryId, @AuthenticationPrincipal AuthUser me) {
        return reference.cities(me.id(), countryId);
    }

    @GetMapping("/cities/{cityId}/applications")
    public List<MobileApplication> applications(@PathVariable Long cityId, @AuthenticationPrincipal AuthUser me) {
        return reference.applications(me.id(), cityId);
    }

    @PostMapping("/persons")
    public ResponseEntity<SubmitResponse> submit(@Valid @RequestBody PersonRequest req,
                                                 @AuthenticationPrincipal AuthUser me) {
        SubmitResult r = persons.submit(req, me);
        return ResponseEntity.status(r.created() ? HttpStatus.CREATED : HttpStatus.OK).body(r.response());
    }

    @PostMapping(value = "/persons/{clientUuid}/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentResponse uploadDocument(@PathVariable UUID clientUuid, @RequestParam("file") MultipartFile file,
                                           @AuthenticationPrincipal AuthUser me) throws IOException {
        byte[] bytes = file.getBytes();
        String ext = detectImageExtension(bytes);
        if (ext == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "Seuls les formats JPEG et PNG sont acceptés.");
        }
        return persons.attachDocument(clientUuid.toString(), bytes, ext, me);
    }

    /** Détection par signature (on ne fait pas confiance au Content-Type envoyé par le client). */
    private static String detectImageExtension(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        return null;
    }
}
