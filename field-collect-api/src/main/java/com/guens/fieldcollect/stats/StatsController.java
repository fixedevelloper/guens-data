package com.guens.fieldcollect.stats;

import com.guens.fieldcollect.person.CaptureMode;
import com.guens.fieldcollect.person.PersonFilter;
import com.guens.fieldcollect.stats.StatsDtos.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/stats")
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    private static PersonFilter filter(Long countryId, Long cityId, Long applicationId, Long agentId,
                                       CaptureMode mode, LocalDate from, LocalDate to) {
        return new PersonFilter(null, countryId, cityId, applicationId, agentId, mode, from, to);
    }

    @GetMapping("/overview")
    public Overview overview(@RequestParam(required = false) Long countryId, @RequestParam(required = false) Long cityId,
                             @RequestParam(required = false) Long applicationId, @RequestParam(required = false) Long agentId,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stats.overview(filter(countryId, cityId, applicationId, agentId, null, from, to));
    }

    @GetMapping("/by-country")
    public List<CountryCount> byCountry(@RequestParam(required = false) Long applicationId,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stats.byCountry(filter(null, null, applicationId, null, null, from, to));
    }

    @GetMapping("/by-city")
    public List<CityCount> byCity(@RequestParam(required = false) Long countryId,
                                  @RequestParam(required = false) Long applicationId,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stats.byCity(filter(countryId, null, applicationId, null, null, from, to));
    }

    @GetMapping("/by-application")
    public List<ApplicationCount> byApplication(@RequestParam(required = false) Long countryId,
                                                @RequestParam(required = false) Long cityId,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stats.byApplication(filter(countryId, cityId, null, null, null, from, to));
    }

    @GetMapping("/breakdown")
    public List<Breakdown> breakdown(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stats.breakdown(filter(null, null, null, null, null, from, to));
    }

    @GetMapping("/timeline")
    public List<DayCount> timeline(@RequestParam(required = false) Long countryId, @RequestParam(required = false) Long cityId,
                                   @RequestParam(required = false) Long applicationId,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stats.timeline(filter(countryId, cityId, applicationId, null, null, from, to));
    }
}
