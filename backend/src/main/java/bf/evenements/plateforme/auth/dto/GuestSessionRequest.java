package bf.evenements.plateforme.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Opens a session for a visitor who buys a ticket / registers without creating
 * an account. A phone number is always required; the e-mail is optional
 * (needed only to claim a real account later, see {@link CompleteRegistrationRequest}).
 * Neither {@code firstName} nor {@code lastName} is {@code @NotBlank} here — a
 * free ticket category can ask for just one of the two (see
 * {@code IdentiteRequise.PRENOM_SEUL}/{@code NOM_SEUL}) — but {@link
 * bf.evenements.plateforme.auth.AuthService#guestSession} still requires at
 * least one of them to be filled.
 */
public record GuestSessionRequest(
        @Email @Size(max = 180) String email,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9 ]{6,20}$", message = "numero de telephone invalide")
        String phone) {
}
