package com.guens.fieldcollect.stats;

import com.guens.fieldcollect.person.CaptureMode;

import java.time.LocalDate;
import java.util.Map;

public final class StatsDtos {
    private StatsDtos() {}

    public record Overview(long total, long today, long last7Days, long last30Days, long activeAgents,
                           Map<CaptureMode, Long> byCaptureMode) {}

    public record CountryCount(Long countryId, String country, long count) {}

    public record CityCount(Long countryId, String country, Long cityId, String city, long count) {}

    public record ApplicationCount(Long applicationId, String application, long count) {}

    public record Breakdown(Long countryId, String country, Long cityId, String city,
                            Long applicationId, String application, long count) {}

    public record DayCount(LocalDate day, long count) {}
}
