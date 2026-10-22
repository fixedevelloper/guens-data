package com.guens.fieldcollect.stats;

import com.guens.fieldcollect.application.FieldApplication;
import com.guens.fieldcollect.application.FieldApplicationRepository;
import com.guens.fieldcollect.geo.City;
import com.guens.fieldcollect.geo.CityRepository;
import com.guens.fieldcollect.geo.Country;
import com.guens.fieldcollect.geo.CountryRepository;
import com.guens.fieldcollect.person.CaptureMode;
import com.guens.fieldcollect.person.Person;
import com.guens.fieldcollect.person.PersonFilter;
import com.guens.fieldcollect.person.PersonRepository;
import com.guens.fieldcollect.stats.StatsDtos.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/** Agrégations SQL (GROUP BY) sur `persons` ; les libellés sont résolus par lot ensuite. Dates en UTC. */
@Service
@Transactional(readOnly = true)
public class StatsService {

    private final EntityManager em;
    private final PersonRepository persons;
    private final CountryRepository countries;
    private final CityRepository cities;
    private final FieldApplicationRepository apps;

    public StatsService(EntityManager em, PersonRepository persons, CountryRepository countries,
                        CityRepository cities, FieldApplicationRepository apps) {
        this.em = em;
        this.persons = persons;
        this.countries = countries;
        this.cities = cities;
        this.apps = apps;
    }

    public Overview overview(PersonFilter f) {
        // Les fenêtres "today / 7j / 30j" ignorent from/to du filtre mais respectent les autres critères.
        PersonFilter base = new PersonFilter(f.q(), f.countryId(), f.cityId(), f.applicationId(), f.agentId(),
                f.captureMode(), null, null);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Map<CaptureMode, Long> modes = new EnumMap<>(CaptureMode.class);
        for (CaptureMode m : CaptureMode.values()) {
            modes.put(m, persons.count(new PersonFilter(f.q(), f.countryId(), f.cityId(), f.applicationId(),
                    f.agentId(), m, f.from(), f.to()).toSpec()));
        }
        return new Overview(
                persons.count(f.toSpec()),
                persons.count(since(base, today)),
                persons.count(since(base, today.minusDays(6))),
                persons.count(since(base, today.minusDays(29))),
                distinctAgents(f),
                modes);
    }

    public List<CountryCount> byCountry(PersonFilter f) {
        List<Object[]> rows = grouped(f, "countryId");
        Map<Long, String> names = countryNames(rows, 0);
        return rows.stream().map(r -> new CountryCount((Long) r[0], names.get(r[0]), (Long) r[1])).toList();
    }

    public List<CityCount> byCity(PersonFilter f) {
        List<Object[]> rows = grouped(f, "countryId", "cityId");
        Map<Long, String> cn = countryNames(rows, 0);
        Map<Long, String> ci = cityNames(rows, 1);
        return rows.stream().map(r -> new CityCount((Long) r[0], cn.get(r[0]), (Long) r[1], ci.get(r[1]),
                (Long) r[2])).toList();
    }

    public List<ApplicationCount> byApplication(PersonFilter f) {
        List<Object[]> rows = grouped(f, "applicationId");
        Map<Long, String> names = appNames(rows, 0);
        return rows.stream().map(r -> new ApplicationCount((Long) r[0], names.get(r[0]), (Long) r[1])).toList();
    }

    public List<Breakdown> breakdown(PersonFilter f) {
        List<Object[]> rows = grouped(f, "countryId", "cityId", "applicationId");
        Map<Long, String> cn = countryNames(rows, 0);
        Map<Long, String> ci = cityNames(rows, 1);
        Map<Long, String> an = appNames(rows, 2);
        return rows.stream().map(r -> new Breakdown((Long) r[0], cn.get(r[0]), (Long) r[1], ci.get(r[1]),
                (Long) r[2], an.get(r[2]), (Long) r[3])).toList();
    }

    public List<DayCount> timeline(PersonFilter f) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Person> root = q.from(Person.class);
        Expression<LocalDate> day = root.get("createdAt").as(LocalDate.class);
        q.multiselect(day, cb.count(root)).where(f.toSpec().toPredicate(root, q, cb)).groupBy(day).orderBy(cb.asc(day));
        return em.createQuery(q).getResultList().stream()
                .map(r -> new DayCount(toLocalDate(r[0]), (Long) r[1])).toList();
    }

    // ---- helpers
    private List<Object[]> grouped(PersonFilter f, String... attrs) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
        Root<Person> root = q.from(Person.class);
        List<Selection<?>> sel = new ArrayList<>();
        List<Expression<?>> group = new ArrayList<>();
        for (String a : attrs) {
            sel.add(root.get(a));
            group.add(root.get(a));
        }
        sel.add(cb.count(root));
        q.multiselect(sel).where(f.toSpec().toPredicate(root, q, cb)).groupBy(group)
                .orderBy(cb.desc(cb.count(root)));
        return em.createQuery(q).getResultList();
    }

    private long distinctAgents(PersonFilter f) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> q = cb.createQuery(Long.class);
        Root<Person> root = q.from(Person.class);
        q.select(cb.countDistinct(root.get("agentId"))).where(f.toSpec().toPredicate(root, q, cb));
        return em.createQuery(q).getSingleResult();
    }

    private static org.springframework.data.jpa.domain.Specification<Person> since(PersonFilter base, LocalDate day) {
        return new PersonFilter(base.q(), base.countryId(), base.cityId(), base.applicationId(), base.agentId(),
                base.captureMode(), day, null).toSpec();
    }

    private static LocalDate toLocalDate(Object o) {
        if (o instanceof LocalDate d) return d;
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        return LocalDate.parse(o.toString());
    }

    private static Set<Long> col(List<Object[]> rows, int i) {
        return rows.stream().map(r -> (Long) r[i]).collect(Collectors.toSet());
    }

    private Map<Long, String> countryNames(List<Object[]> rows, int i) {
        return countries.findAllById(col(rows, i)).stream().collect(Collectors.toMap(Country::getId, Country::getName));
    }

    private Map<Long, String> cityNames(List<Object[]> rows, int i) {
        return cities.findAllById(col(rows, i)).stream().collect(Collectors.toMap(City::getId, City::getName));
    }

    private Map<Long, String> appNames(List<Object[]> rows, int i) {
        return apps.findAllById(col(rows, i)).stream()
                .collect(Collectors.toMap(FieldApplication::getId, FieldApplication::getName));
    }
}
