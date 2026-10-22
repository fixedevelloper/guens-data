package com.guens.fieldcollect.application;

import com.guens.fieldcollect.application.ApplicationDtos.*;
import com.guens.fieldcollect.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/applications")
public class ApplicationAdminController {

    private final ApplicationService service;

    public ApplicationAdminController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ApplicationResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(@Valid @RequestBody ApplicationRequest req, @AuthenticationPrincipal AuthUser me) {
        return service.create(req, me.id());
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(@PathVariable Long id, @Valid @RequestBody ApplicationRequest req,
                                      @AuthenticationPrincipal AuthUser me) {
        return service.update(id, req, me.id());
    }

    @PutMapping("/{id}/cities")
    public ApplicationResponse setCities(@PathVariable Long id, @Valid @RequestBody CityIdsRequest req,
                                         @AuthenticationPrincipal AuthUser me) {
        return service.setCities(id, req, me.id());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        service.delete(id, me.id());
        return ResponseEntity.noContent().build();
    }
}
