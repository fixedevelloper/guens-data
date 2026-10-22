package com.guens.fieldcollect.person;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Filtres communs à la consultation, à l'export et aux statistiques. Dates en UTC, bornes incluses. */
public record PersonFilter(String q, Long countryId, Long cityId, Long applicationId, Long agentId,
                           CaptureMode captureMode, LocalDate from, LocalDate to) {

    public Specification<Person> toSpec() {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("lastName")), like),
                        cb.like(cb.lower(root.get("firstName")), like)));
            }
            if (countryId != null) p.add(cb.equal(root.get("countryId"), countryId));
            if (cityId != null) p.add(cb.equal(root.get("cityId"), cityId));
            if (applicationId != null) p.add(cb.equal(root.get("applicationId"), applicationId));
            if (agentId != null) p.add(cb.equal(root.get("agentId"), agentId));
            if (captureMode != null) p.add(cb.equal(root.get("captureMode"), captureMode));
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (to != null) {
                p.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
