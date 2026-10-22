package com.guens.fieldcollect.user;

import com.guens.fieldcollect.application.FieldApplication;
import com.guens.fieldcollect.application.FieldApplicationRepository;
import com.guens.fieldcollect.audit.AuditService;
import com.guens.fieldcollect.auth.RefreshTokenRepository;
import com.guens.fieldcollect.common.dto.PageResponse;
import com.guens.fieldcollect.common.exception.ApiException;
import com.guens.fieldcollect.common.exception.NotFound;
import com.guens.fieldcollect.geo.City;
import com.guens.fieldcollect.geo.CityRepository;
import com.guens.fieldcollect.user.UserDtos.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserAdminService {

    private final UserRepository users;
    private final UserAssignmentRepository assignments;
    private final FieldApplicationRepository apps;
    private final CityRepository cities;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserAdminService(UserRepository users, UserAssignmentRepository assignments,
                            FieldApplicationRepository apps, CityRepository cities,
                            RefreshTokenRepository refreshTokens, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.assignments = assignments;
        this.apps = apps;
        this.cities = cities;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String q, Role role, Boolean active, int page, int size) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("fullName")), like)));
            }
            if (role != null) p.add(cb.equal(root.get("role"), role));
            if (active != null) p.add(cb.equal(root.get("active"), active));
            return cb.and(p.toArray(new Predicate[0]));
        };
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("fullName").ascending());
        return PageResponse.of(users.findAll(spec, pageable), u -> toResponse(u, null));
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        User u = find(id);
        return toResponse(u, assignmentsOf(id));
    }

    public UserResponse create(CreateUserRequest req, Long actor) {
        String username = req.username().trim();
        if (users.existsByUsername(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE", "Ce nom d'utilisateur existe déjà.");
        }
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setFullName(req.fullName().trim());
        u.setPhone(req.phone());
        u.setRole(req.role() == null ? Role.AGENT : req.role());
        u.setCreatedBy(actor);
        users.save(u);
        audit.log(actor, "USER_CREATED", "User", u.getId());
        return toResponse(u, List.of());
    }

    public UserResponse update(Long id, UpdateUserRequest req, Long actor) {
        User u = find(id);
        u.setFullName(req.fullName().trim());
        u.setPhone(req.phone());
        audit.log(actor, "USER_UPDATED", "User", id);
        return toResponse(u, assignmentsOf(id));
    }

    public UserResponse setActive(Long id, boolean active, Long actor) {
        if (!active && id.equals(actor)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_DEACTIVATION", "Vous ne pouvez pas désactiver votre propre compte.");
        }
        User u = find(id);
        u.setActive(active);
        if (!active) refreshTokens.revokeAllForUser(id);
        audit.log(actor, active ? "USER_ACTIVATED" : "USER_DEACTIVATED", "User", id);
        return toResponse(u, assignmentsOf(id));
    }

    /** Libère l'appareil : le prochain login d'un appareil le liera. Les sessions existantes sont coupées. */
    public UserResponse resetDevice(Long id, Long actor) {
        User u = find(id);
        u.setDeviceId(null);
        u.setDeviceModel(null);
        u.setDeviceBoundAt(null);
        refreshTokens.revokeAllForUser(id);
        audit.log(actor, "DEVICE_RESET", "User", id);
        return toResponse(u, assignmentsOf(id));
    }

    public UserResponse unlock(Long id, Long actor) {
        User u = find(id);
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        audit.log(actor, "USER_UNLOCKED", "User", id);
        return toResponse(u, assignmentsOf(id));
    }

    public void resetPassword(Long id, String newPassword, Long actor) {
        User u = find(id);
        u.setPasswordHash(encoder.encode(newPassword));
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        refreshTokens.revokeAllForUser(id);
        audit.log(actor, "PASSWORD_RESET", "User", id);
    }

    public UserResponse setAssignments(Long id, AssignmentsRequest req, Long actor) {
        User u = find(id);
        if (u.getRole() != Role.AGENT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_AN_AGENT", "Seuls les agents ont des affectations.");
        }
        Set<UserAssignmentId> wanted = new HashSet<>();
        for (AssignmentItem item : req.assignments()) {
            FieldApplication app = apps.findById(item.applicationId())
                    .orElseThrow(() -> NotFound.of("Application", item.applicationId()));
            boolean deployed = app.getCities().stream().anyMatch(c -> c.getId().equals(item.cityId()));
            if (!deployed) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "APPLICATION_NOT_IN_CITY",
                        "L'application " + app.getCode() + " n'est pas déployée dans la ville " + item.cityId() + ".");
            }
            wanted.add(new UserAssignmentId(id, item.applicationId(), item.cityId()));
        }
        // Diff plutôt que delete+insert : Hibernate exécute les INSERT avant les DELETE (violation de PK).
        List<UserAssignment> current = assignments.findByIdUserId(id);
        Set<UserAssignmentId> currentIds = current.stream().map(UserAssignment::getId).collect(Collectors.toSet());
        assignments.deleteAll(current.stream().filter(a -> !wanted.contains(a.getId())).toList());
        assignments.saveAll(wanted.stream().filter(w -> !currentIds.contains(w)).map(UserAssignment::new).toList());
        assignments.flush();
        audit.log(actor, "USER_ASSIGNMENTS_SET", "User", id);
        return toResponse(u, assignmentsOf(id));
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> NotFound.of("Utilisateur", id));
    }

    private List<AssignmentResponse> assignmentsOf(Long userId) {
        List<UserAssignmentId> ids = assignments.findByIdUserId(userId).stream().map(UserAssignment::getId).toList();
        Map<Long, String> appNames = apps.findAllById(ids.stream().map(UserAssignmentId::getApplicationId).toList())
                .stream().collect(Collectors.toMap(FieldApplication::getId, FieldApplication::getName));
        Map<Long, String> cityNames = cities.findAllById(ids.stream().map(UserAssignmentId::getCityId).toList())
                .stream().collect(Collectors.toMap(City::getId, City::getName));
        return ids.stream()
                .map(i -> new AssignmentResponse(i.getApplicationId(), appNames.get(i.getApplicationId()),
                        i.getCityId(), cityNames.get(i.getCityId())))
                .sorted(Comparator.comparing(AssignmentResponse::applicationName)
                        .thenComparing(AssignmentResponse::cityName))
                .toList();
    }

    private static UserResponse toResponse(User u, List<AssignmentResponse> a) {
        return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getPhone(), u.getRole(), u.isActive(),
                u.getDeviceId(), u.getDeviceModel(), u.getDeviceBoundAt(), u.getLastLoginAt(), u.getLockedUntil(), a);
    }
}
