package bf.evenements.plateforme.auth.dto;

import bf.evenements.plateforme.user.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-service account creation. Only {@code PARTICULIER} and {@code STRUCTURE}
 * are accepted here; organiser / admin accounts are provisioned by an admin.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9 ]{6,20}$", message = "numero de telephone invalide")
        String phone,
        UserType type) {

    public UserType resolvedType() {
        return type == UserType.STRUCTURE ? UserType.STRUCTURE : UserType.PARTICULIER;
    }
}
