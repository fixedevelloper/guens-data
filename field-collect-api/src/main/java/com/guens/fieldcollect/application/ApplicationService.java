package com.guens.fieldcollect.application;

import com.guens.fieldcollect.application.ApplicationDtos.*;
import com.guens.fieldcollect.audit.AuditService;
import com.guens.fieldcollect.common.exception.ApiException;
import com.guens.fieldcollect.common.exception.NotFound;
import com.guens.fieldcollect.geo.City;
import com.guens.fieldcollect.geo.CityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class ApplicationService {

    private final FieldApplicationRepository apps;
    private final CityRepository cities;
    private final AuditService audit;

    public ApplicationService(FieldApplicationRepository apps, CityRepository cities, AuditService audit) {
        this.apps = apps;
        this.cities = cities;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> list() {
        return apps.findAllByOrderByNameAsc().stream().map(ApplicationService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(Long id) {
        return toResponse(find(id));
    }

    public ApplicationResponse create(ApplicationRequest req, Long actor) {
        String code = req.code().trim().toUpperCase();
        if (apps.existsByCode(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE", "Ce code d'application existe déjà.");
        }
        FieldApplication a = new FieldApplication();
        a.setCode(code);
        a.setName(req.name().trim());
        a.setDescription(req.description());
        a.setActive(req.active() == null || req.active());
        apps.save(a);
        audit.log(actor, "APPLICATION_CREATED", "Application", a.getId());
        return toResponse(a);
    }

    public ApplicationResponse update(Long id, ApplicationRequest req, Long actor) {
        FieldApplication a = find(id);
        String code = req.code().trim().toUpperCase();
        if (!a.getCode().equals(code) && apps.existsByCode(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE", "Ce code d'application existe déjà.");
        }
        a.setCode(code);
        a.setName(req.name().trim());
        a.setDescription(req.description());
        if (req.active() != null) a.setActive(req.active());
        audit.log(actor, "APPLICATION_UPDATED", "Application", id);
        return toResponse(a);
    }

    public ApplicationResponse setCities(Long id, CityIdsRequest req, Long actor) {
        FieldApplication a = find(id);
        Set<Long> wanted = new HashSet<>(req.cityIds());
        List<City> found = cities.findAllById(wanted);
        if (found.size() != wanted.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_CITY", "Une ou plusieurs villes sont inconnues.");
        }
        a.getCities().clear();
        a.getCities().addAll(found);
        audit.log(actor, "APPLICATION_CITIES_SET", "Application", id);
        return toResponse(a);
    }

    public void delete(Long id, Long actor) {
        FieldApplication a = find(id);
        apps.delete(a);
        apps.flush();
        audit.log(actor, "APPLICATION_DELETED", "Application", id);
    }

    private FieldApplication find(Long id) {
        return apps.findById(id).orElseThrow(() -> NotFound.of("Application", id));
    }

    private static ApplicationResponse toResponse(FieldApplication a) {
        List<Long> cityIds = a.getCities().stream().map(City::getId).sorted().collect(Collectors.toList());
        return new ApplicationResponse(a.getId(), a.getCode(), a.getName(), a.getDescription(), a.isActive(), cityIds);
    }
}
