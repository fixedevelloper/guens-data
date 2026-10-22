package com.guens.fieldcollect.security;

import com.guens.fieldcollect.common.exception.ErrorResponseWriter;
import com.guens.fieldcollect.user.Role;
import com.guens.fieldcollect.user.User;
import com.guens.fieldcollect.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authentifie via le JWT, puis applique la règle « 1 agent = 1 appareil » à CHAQUE requête :
 * le header X-Device-Id doit correspondre à l'appareil actuellement lié en base
 * (donc un reset-device invalide immédiatement les tokens existants).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String DEVICE_HEADER = "X-Device-Id";

    private final JwtService jwt;
    private final UserRepository users;
    private final ErrorResponseWriter errors;

    public JwtAuthenticationFilter(JwtService jwt, UserRepository users, ErrorResponseWriter errors) {
        this.jwt = jwt;
        this.users = users;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }
        Optional<AuthUser> parsed = jwt.parse(header.substring(7));
        Optional<User> user = parsed.flatMap(p -> users.findById(p.id()));
        if (user.isEmpty() || !user.get().isActive()) {
            chain.doFilter(req, res); // non authentifié -> 401 par l'entry point
            return;
        }
        User u = user.get();
        if (u.getRole() == Role.AGENT) {
            String sent = req.getHeader(DEVICE_HEADER);
            if (u.getDeviceId() == null || !u.getDeviceId().equals(sent)
                    || !u.getDeviceId().equals(parsed.get().deviceId())) {
                errors.write(res, 403, "DEVICE_MISMATCH",
                        "Cet appareil n'est pas autorisé pour ce compte.");
                return;
            }
        }
        AuthUser principal = new AuthUser(u.getId(), u.getUsername(), u.getRole(), u.getDeviceId());
        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(req, res);
    }
}
