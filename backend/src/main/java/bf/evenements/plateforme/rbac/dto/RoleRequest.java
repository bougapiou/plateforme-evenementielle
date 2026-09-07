package bf.evenements.plateforme.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record RoleRequest(
        @NotBlank @Size(max = 60)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "nom en MAJUSCULES_SNAKE_CASE")
        String name,
        @Size(max = 255) String description,
        @NotNull Set<String> permissions) {
}
