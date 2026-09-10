package bf.evenements.plateforme.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Opens a session for a visitor who buys a ticket / registers without creating
 * an account. A name and a phone number are required; the e-mail is optional
 * (needed only to claim a real account later, see {@link CompleteRegistrationRequest}).
 */
public record GuestSessionRequest(
        @Email @Size(max = 180) String email,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9 ]{6,20}$", message = "numero de telephone invalide")
        String phone) {
}
