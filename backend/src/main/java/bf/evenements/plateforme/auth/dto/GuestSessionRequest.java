package bf.evenements.plateforme.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Opens a session for a visitor who buys a ticket / registers without creating
 * an account. Only an e-mail and a name are required; the visitor can claim the
 * account later with {@link CompleteRegistrationRequest}.
 */
public record GuestSessionRequest(
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Pattern(regexp = "^$|^\\+?[0-9 ]{6,20}$", message = "numero de telephone invalide")
        String phone) {
}
