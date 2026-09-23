package bf.evenements.plateforme.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lets a real POST stand in for PUT/PATCH/DELETE, via an
 * {@code X-App-Verb} header. Added because a network appliance
 * in front of production (ANPTIC's gateway) silently drops PUT/PATCH/DELETE
 * with a bare 403 before the request ever reaches this server — confirmed by
 * comparing an identical GET (reaches us, 401) against a PUT (never logged
 * here at all). GET/POST/OPTIONS are unaffected, so this filter only ever
 * rewrites a POST that explicitly asks to be treated as something else;
 * every other request passes through untouched. Runs first in the security
 * chain so every downstream check (route matching, {@code @PreAuthorize},
 * authorizeHttpRequests) sees the overridden method.
 */
@Component
public class MethodOverrideFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-App-Verb";
    private static final Set<String> ALLOWED = Set.of("PUT", "PATCH", "DELETE");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String override = request.getHeader(HEADER);
        if ("POST".equalsIgnoreCase(request.getMethod()) && override != null) {
            String method = override.trim().toUpperCase();
            if (ALLOWED.contains(method)) {
                filterChain.doFilter(new MethodOverrideRequest(request, method), response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static final class MethodOverrideRequest extends HttpServletRequestWrapper {
        private final String method;

        MethodOverrideRequest(HttpServletRequest request, String method) {
            super(request);
            this.method = method;
        }

        @Override
        public String getMethod() {
            return method;
        }
    }
}
