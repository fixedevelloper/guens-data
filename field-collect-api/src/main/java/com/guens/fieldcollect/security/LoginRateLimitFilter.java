package com.guens.fieldcollect.security;

import com.guens.fieldcollect.common.exception.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Limite le nombre d'appels par adresse IP sur login/refresh (fenêtre fixe d'une minute, en mémoire).
 * Complète le verrouillage par compte : il freine le « password spraying » sur plusieurs comptes.
 * Limite : le compteur est local à l'instance (à remplacer par Redis si plusieurs instances).
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;
    private static final int PURGE_THRESHOLD = 10_000;
    private static final Set<String> PATHS = Set.of("/api/v1/auth/login", "/api/v1/auth/refresh");

    private record Window(long start, int count) {}

    private final int limitPerMinute;
    private final LongSupplier clock;
    private final ErrorResponseWriter errors;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(int limitPerMinute, ErrorResponseWriter errors) {
        this(limitPerMinute, errors, System::currentTimeMillis);
    }

    public LoginRateLimitFilter(int limitPerMinute, ErrorResponseWriter errors, LongSupplier clock) {
        this.limitPerMinute = limitPerMinute;
        this.errors = errors;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !"POST".equals(req.getMethod()) || !PATHS.contains(req.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.getAsLong();
        if (windows.size() > PURGE_THRESHOLD) {
            windows.values().removeIf(w -> now - w.start() >= WINDOW_MS);
        }
        Window w = windows.compute(req.getRemoteAddr(),
                (k, cur) -> cur == null || now - cur.start() >= WINDOW_MS
                        ? new Window(now, 1) : new Window(cur.start(), cur.count() + 1));
        if (w.count() > limitPerMinute) {
            long retryAfter = Math.max(1, (WINDOW_MS - (now - w.start()) + 999) / 1000);
            res.setHeader("Retry-After", String.valueOf(retryAfter));
            errors.write(res, 429, "TOO_MANY_REQUESTS", "Trop de tentatives. Réessayez dans quelques instants.");
            return;
        }
        chain.doFilter(req, res);
    }
}
