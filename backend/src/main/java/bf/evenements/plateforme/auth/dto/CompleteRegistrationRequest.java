package bf.evenements.plateforme.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Turns a guest account into a full one by choosing a password. An {@code email}
 * is required only when the guest was created without one (phone-only checkout).
 */
public record CompleteRegistrationRequest(
        @NotBlank @Size(min = 8, max = 72) String password,
        @Email @Size(max = 180) String email,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName) {
}
