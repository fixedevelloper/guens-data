package com.guens.fieldcollect.user;

import com.guens.fieldcollect.common.dto.PageResponse;
import com.guens.fieldcollect.security.AuthUser;
import com.guens.fieldcollect.user.UserDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<UserResponse> search(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) Role role,
                                             @RequestParam(required = false) Boolean active,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.search(q, role, active, page, size);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest req, @AuthenticationPrincipal AuthUser me) {
        return service.create(req, me.id());
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req,
                               @AuthenticationPrincipal AuthUser me) {
        return service.update(id, req, me.id());
    }

    @PatchMapping("/{id}/status")
    public UserResponse status(@PathVariable Long id, @Valid @RequestBody StatusRequest req,
                               @AuthenticationPrincipal AuthUser me) {
        return service.setActive(id, req.active(), me.id());
    }

    @PutMapping("/{id}/assignments")
    public UserResponse assignments(@PathVariable Long id, @Valid @RequestBody AssignmentsRequest req,
                                    @AuthenticationPrincipal AuthUser me) {
        return service.setAssignments(id, req, me.id());
    }

    @PostMapping("/{id}/reset-device")
    public UserResponse resetDevice(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        return service.resetDevice(id, me.id());
    }

    @PostMapping("/{id}/unlock")
    public UserResponse unlock(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        return service.unlock(id, me.id());
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest req,
                                              @AuthenticationPrincipal AuthUser me) {
        service.resetPassword(id, req.newPassword(), me.id());
        return ResponseEntity.noContent().build();
    }
}
