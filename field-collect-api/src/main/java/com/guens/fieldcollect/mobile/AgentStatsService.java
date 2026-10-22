package com.guens.fieldcollect.mobile;

import com.guens.fieldcollect.person.PersonFilter;
import com.guens.fieldcollect.stats.StatsDtos.*;
import com.guens.fieldcollect.stats.StatsService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Statistiques de l'agent connecté : uniquement des compteurs agrégés sur SES enregistrements.
 * Aucune donnée de personne n'est restituée (règle « l'agent ne relit rien »). Jours en UTC.
 */
@Service
public class AgentStatsService {

    public static final int MAX_DAYS = 90;

    public record Summary(long total, long today, long last7Days, long last30Days,
                          List<CountryCount> byCountry, List<CityCount> byCity,
                          List<ApplicationCount> byApplication) {}

    public record DayDetail(LocalDate date, long total, List<CountryCount> byCountry, List<CityCount> byCity,
                            List<ApplicationCount> byApplication) {}

    private final StatsService stats;

    public AgentStatsService(StatsService stats) {
        this.stats = stats;
    }

    private static PersonFilter mine(Long agentId, LocalDate from, LocalDate to) {
        return new PersonFilter(null, null, null, null, agentId, null, from, to);
    }

    public Summary summary(Long agentId) {
        PersonFilter all = mine(agentId, null, null);
        Overview o = stats.overview(all);
        return new Summary(o.total(), o.today(), o.last7Days(), o.last30Days(),
                stats.byCountry(all), stats.byCity(all), stats.byApplication(all));
    }

    /** Les {@code days} derniers jours (aujourd'hui inclus), jours sans enregistrement à 0, du plus ancien au plus récent. */
    public List<DayCount> daily(Long agentId, int days) {
        int n = Math.max(1, Math.min(days, MAX_DAYS));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = today.minusDays(n - 1L);
        Map<LocalDate, Long> counts = stats.timeline(mine(agentId, from, today)).stream()
                .collect(Collectors.toMap(DayCount::day, DayCount::count, Long::sum));
        List<DayCount> out = new ArrayList<>(n);
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            out.add(new DayCount(d, counts.getOrDefault(d, 0L)));
        }
        return out;
    }

    public DayDetail day(Long agentId, LocalDate date) {
        PersonFilter f = mine(agentId, date, date);
        List<CountryCount> byCountry = stats.byCountry(f);
        long total = byCountry.stream().mapToLong(CountryCount::count).sum();
        return new DayDetail(date, total, byCountry, stats.byCity(f), stats.byApplication(f));
    }
}
