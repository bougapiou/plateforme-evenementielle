package bf.evenements.plateforme.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Opens a guest session from a phone number alone — used to claim a free
 * ticket category the organizer marked as not requiring a form. See
 * {@link GuestSessionRequest} for the normal (name + phone) guest checkout.
 */
public record GuestQuickSessionRequest(
        @NotBlank
        @Pattern(regexp = "^\\+?[0-9 ]{6,20}$", message = "numero de telephone invalide")
        String phone) {
}
