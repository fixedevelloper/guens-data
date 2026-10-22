package com.guens.fieldcollect.geo;

import com.guens.fieldcollect.audit.AuditService;
import com.guens.fieldcollect.common.exception.ApiException;
import com.guens.fieldcollect.common.exception.NotFound;
import com.guens.fieldcollect.geo.GeoDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class GeoService {

    private final CountryRepository countries;
    private final CityRepository cities;
    private final AuditService audit;

    public GeoService(CountryRepository countries, CityRepository cities, AuditService audit) {
        this.countries = countries;
        this.cities = cities;
        this.audit = audit;
    }

    // ----- pays
    @Transactional(readOnly = true)
    public List<CountryResponse> listCountries() {
        return countries.findAllByOrderByNameAsc().stream().map(GeoService::toResponse).toList();
    }

    public CountryResponse createCountry(CountryRequest req, Long actor) {
        String iso = req.isoCode().trim().toUpperCase();
        if (countries.existsByIsoCode(iso) || countries.existsByName(req.name().trim())) {
            throw conflict("Ce pays existe déjà.");
        }
        Country c = new Country();
        c.setIsoCode(iso);
        c.setName(req.name().trim());
        c.setActive(req.active() == null || req.active());
        countries.save(c);
        audit.log(actor, "COUNTRY_CREATED", "Country", c.getId());
        return toResponse(c);
    }

    public CountryResponse updateCountry(Long id, CountryRequest req, Long actor) {
        Country c = countries.findById(id).orElseThrow(() -> NotFound.of("Pays", id));
        String iso = req.isoCode().trim().toUpperCase();
        String name = req.name().trim();
        if (!c.getIsoCode().equals(iso) && countries.existsByIsoCode(iso)) throw conflict("Code ISO déjà utilisé.");
        if (!c.getName().equals(name) && countries.existsByName(name)) throw conflict("Nom déjà utilisé.");
        c.setIsoCode(iso);
        c.setName(name);
        if (req.active() != null) c.setActive(req.active());
        audit.log(actor, "COUNTRY_UPDATED", "Country", id);
        return toResponse(c);
    }

    public void deleteCountry(Long id, Long actor) {
        Country c = countries.findById(id).orElseThrow(() -> NotFound.of("Pays", id));
        countries.delete(c);
        countries.flush(); // déclenche l'erreur d'intégrité (409) ici si des villes/données y sont liées
        audit.log(actor, "COUNTRY_DELETED", "Country", id);
    }

    // ----- villes
    @Transactional(readOnly = true)
    public List<CityResponse> listCities(Long countryId) {
        List<City> list = countryId == null ? cities.findAllByOrderByNameAsc()
                : cities.findByCountryIdOrderByNameAsc(countryId);
        return list.stream().map(GeoService::toResponse).toList();
    }

    public CityResponse createCity(CityRequest req, Long actor) {
        Country country = countries.findById(req.countryId())
                .orElseThrow(() -> NotFound.of("Pays", req.countryId()));
        String name = req.name().trim();
        if (cities.existsByCountryIdAndName(country.getId(), name)) throw conflict("Cette ville existe déjà dans ce pays.");
        City c = new City();
        c.setCountry(country);
        c.setName(name);
        c.setActive(req.active() == null || req.active());
        cities.save(c);
        audit.log(actor, "CITY_CREATED", "City", c.getId());
        return toResponse(c);
    }

    public CityResponse updateCity(Long id, CityRequest req, Long actor) {
        City c = cities.findById(id).orElseThrow(() -> NotFound.of("Ville", id));
        Country country = countries.findById(req.countryId())
                .orElseThrow(() -> NotFound.of("Pays", req.countryId()));
        String name = req.name().trim();
        boolean changed = !c.getCountry().getId().equals(country.getId()) || !c.getName().equals(name);
        if (changed && cities.existsByCountryIdAndName(country.getId(), name)) {
            throw conflict("Cette ville existe déjà dans ce pays.");
        }
        c.setCountry(country);
        c.setName(name);
        if (req.active() != null) c.setActive(req.active());
        audit.log(actor, "CITY_UPDATED", "City", id);
        return toResponse(c);
    }

    public void deleteCity(Long id, Long actor) {
        City c = cities.findById(id).orElseThrow(() -> NotFound.of("Ville", id));
        cities.delete(c);
        cities.flush();
        audit.log(actor, "CITY_DELETED", "City", id);
    }

    private static ApiException conflict(String msg) {
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE", msg);
    }

    private static CountryResponse toResponse(Country c) {
        return new CountryResponse(c.getId(), c.getIsoCode(), c.getName(), c.isActive());
    }

    private static CityResponse toResponse(City c) {
        return new CityResponse(c.getId(), c.getCountry().getId(), c.getCountry().getName(), c.getName(), c.isActive());
    }
}
