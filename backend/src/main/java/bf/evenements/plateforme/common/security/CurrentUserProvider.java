package bf.evenements.plateforme.common.security;

import bf.evenements.plateforme.common.exception.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Service-layer access to the authenticated principal, decoupled from the web tier.
 */
@Component
public class CurrentUserProvider {

    public Optional<AuthenticatedUser> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public AuthenticatedUser require() {
        return current().orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentification requise.") {});
    }

    public UUID requireId() {
        return require().id();
    }

    public boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }
}
