package bf.evenements.plateforme.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Asks for a password-reset link to be e-mailed to {@code email}. */
public record ForgotPasswordRequest(
        @NotBlank @Email @Size(max = 180) String email) {
}
