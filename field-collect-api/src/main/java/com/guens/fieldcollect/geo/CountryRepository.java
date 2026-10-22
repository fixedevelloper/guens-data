package com.guens.fieldcollect.geo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CountryRepository extends JpaRepository<Country, Long> {
    List<Country> findAllByOrderByNameAsc();
    boolean existsByIsoCode(String isoCode);
    boolean existsByName(String name);
}
