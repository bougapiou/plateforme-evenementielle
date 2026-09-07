package bf.evenements.plateforme.user.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record UpdateUserRolesRequest(@NotNull Set<String> roles) {
}
