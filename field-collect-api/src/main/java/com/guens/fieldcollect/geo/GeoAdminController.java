package com.guens.fieldcollect.geo;

import com.guens.fieldcollect.geo.GeoDtos.*;
import com.guens.fieldcollect.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class GeoAdminController {

    private final GeoService geo;

    public GeoAdminController(GeoService geo) {
        this.geo = geo;
    }

    @GetMapping("/countries")
    public List<CountryResponse> countries() {
        return geo.listCountries();
    }

    @PostMapping("/countries")
    @ResponseStatus(HttpStatus.CREATED)
    public CountryResponse createCountry(@Valid @RequestBody CountryRequest req, @AuthenticationPrincipal AuthUser me) {
        return geo.createCountry(req, me.id());
    }

    @PutMapping("/countries/{id}")
    public CountryResponse updateCountry(@PathVariable Long id, @Valid @RequestBody CountryRequest req,
                                         @AuthenticationPrincipal AuthUser me) {
        return geo.updateCountry(id, req, me.id());
    }

    @DeleteMapping("/countries/{id}")
    public ResponseEntity<Void> deleteCountry(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        geo.deleteCountry(id, me.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cities")
    public List<CityResponse> cities(@RequestParam(required = false) Long countryId) {
        return geo.listCities(countryId);
    }

    @PostMapping("/cities")
    @ResponseStatus(HttpStatus.CREATED)
    public CityResponse createCity(@Valid @RequestBody CityRequest req, @AuthenticationPrincipal AuthUser me) {
        return geo.createCity(req, me.id());
    }

    @PutMapping("/cities/{id}")
    public CityResponse updateCity(@PathVariable Long id, @Valid @RequestBody CityRequest req,
                                   @AuthenticationPrincipal AuthUser me) {
        return geo.updateCity(id, req, me.id());
    }

    @DeleteMapping("/cities/{id}")
    public ResponseEntity<Void> deleteCity(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        geo.deleteCity(id, me.id());
        return ResponseEntity.noContent().build();
    }
}
