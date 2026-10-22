package com.guens.fieldcollect.mobile;

import com.guens.fieldcollect.application.FieldApplication;
import com.guens.fieldcollect.application.FieldApplicationRepository;
import com.guens.fieldcollect.geo.City;
import com.guens.fieldcollect.geo.CityRepository;
import com.guens.fieldcollect.geo.Country;
import com.guens.fieldcollect.geo.GeoDtos.CityResponse;
import com.guens.fieldcollect.geo.GeoDtos.CountryResponse;
import com.guens.fieldcollect.user.UserAssignment;
import com.guens.fieldcollect.user.UserAssignmentId;
import com.guens.fieldcollect.user.UserAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** Listes de sélection de l'agent : uniquement son périmètre affecté, et seulement les éléments actifs. */
@Service
@Transactional(readOnly = true)
public class MobileReferenceService {

    public record MobileApplication(Long id, String code, String name, String description) {}

    private record Scope(FieldApplication app, City city) {}

    private final UserAssignmentRepository assignments;
    private final FieldApplicationRepository apps;
    private final CityRepository cities;

    public MobileReferenceService(UserAssignmentRepository assignments, FieldApplicationRepository apps,
                                  CityRepository cities) {
        this.assignments = assignments;
        this.apps = apps;
        this.cities = cities;
    }

    public List<CountryResponse> countries(Long userId) {
        return scope(userId).stream().map(s -> s.city().getCountry())
                .collect(Collectors.toMap(Country::getId, c -> c, (a, b) -> a)).values().stream()
                .sorted(Comparator.comparing(Country::getName))
                .map(c -> new CountryResponse(c.getId(), c.getIsoCode(), c.getName(), c.isActive()))
                .toList();
    }

    public List<CityResponse> cities(Long userId, Long countryId) {
        return scope(userId).stream().map(Scope::city)
                .filter(c -> c.getCountry().getId().equals(countryId))
                .collect(Collectors.toMap(City::getId, c -> c, (a, b) -> a)).values().stream()
                .sorted(Comparator.comparing(City::getName))
                .map(c -> new CityResponse(c.getId(), c.getCountry().getId(), c.getCountry().getName(),
                        c.getName(), c.isActive()))
                .toList();
    }

    public List<MobileApplication> applications(Long userId, Long cityId) {
        return scope(userId).stream()
                .filter(s -> s.city().getId().equals(cityId))
                .map(Scope::app)
                .sorted(Comparator.comparing(FieldApplication::getName))
                .map(a -> new MobileApplication(a.getId(), a.getCode(), a.getName(), a.getDescription()))
                .toList();
    }

    private List<Scope> scope(Long userId) {
        List<UserAssignmentId> ids = assignments.findByIdUserId(userId).stream()
                .map(UserAssignment::getId).toList();
        Map<Long, FieldApplication> appById = apps.findAllById(
                ids.stream().map(UserAssignmentId::getApplicationId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(FieldApplication::getId, a -> a));
        Map<Long, City> cityById = cities.findAllById(
                ids.stream().map(UserAssignmentId::getCityId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(City::getId, c -> c));
        List<Scope> out = new ArrayList<>();
        for (UserAssignmentId i : ids) {
            FieldApplication a = appById.get(i.getApplicationId());
            City c = cityById.get(i.getCityId());
            if (a != null && c != null && a.isActive() && c.isActive() && c.getCountry().isActive()) {
                out.add(new Scope(a, c));
            }
        }
        return out;
    }
}
