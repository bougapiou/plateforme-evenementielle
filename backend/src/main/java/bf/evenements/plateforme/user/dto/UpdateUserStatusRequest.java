package bf.evenements.plateforme.user.dto;

import bf.evenements.plateforme.user.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull UserStatus status) {
}
