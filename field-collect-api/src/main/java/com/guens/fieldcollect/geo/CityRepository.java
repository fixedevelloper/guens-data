package com.guens.fieldcollect.geo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CityRepository extends JpaRepository<City, Long> {
    List<City> findAllByOrderByNameAsc();
    List<City> findByCountryIdOrderByNameAsc(Long countryId);
    boolean existsByCountryIdAndName(Long countryId, String name);
}
