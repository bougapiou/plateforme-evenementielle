package bf.evenements.plateforme.common.security;

import java.util.UUID;

/**
 * Lightweight principal derived from the access token claims and exposed to
 * controllers via {@link CurrentUser}.
 */
public record AuthenticatedUser(UUID id, String email, String name) {
}
