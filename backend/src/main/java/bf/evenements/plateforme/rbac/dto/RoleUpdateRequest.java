package bf.evenements.plateforme.rbac.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Modification d'un rôle personnalisé : description et permissions (le nom reste fixe). */
public record RoleUpdateRequest(
        @Size(max = 255) String description,
        @NotNull Set<String> permissions) {
}
