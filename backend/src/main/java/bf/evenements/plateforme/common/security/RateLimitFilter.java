package bf.evenements.plateforme.common.security;

import bf.evenements.plateforme.common.web.HttpUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lightweight fixed-window rate limiter for the sensitive authentication
 * endpoints (login / register / password reset): guards against credential
 * stuffing and reset-link spam without an external dependency. Per client IP.
 * Disabled when the limit is {@code <= 0}.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Value("${app.security.rate-limit-per-minute:20}")
    private int maxRequests;

    private final SecurityErrorWriter errorWriter;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(Instant start, AtomicInteger count) {
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (maxRequests <= 0) {
            return true;
        }
        String uri = request.getRequestURI();
        return !("POST".equals(request.getMethod())
                && (uri.equals("/api/auth/login")
                        || uri.equals("/api/auth/register")
                        || uri.equals("/api/auth/password/forgot")
                        || uri.equals("/api/auth/password/reset")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = HttpUtils.clientIp(request) + ":" + request.getRequestURI();
        Instant now = Instant.now();
        Window window = windows.compute(key, (k, existing) ->
                (existing == null || now.isAfter(existing.start().plus(WINDOW)))
                        ? new Window(now, new AtomicInteger(0)) : existing);

        if (window.count().incrementAndGet() > maxRequests) {
            response.setHeader("Retry-After", String.valueOf(WINDOW.getSeconds()));
            errorWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Trop de tentatives. Réessayez dans une minute.");
            return;
        }
        filterChain.doFilter(request, response);

        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> now.isAfter(e.getValue().start().plus(WINDOW)));
        }
    }
}
