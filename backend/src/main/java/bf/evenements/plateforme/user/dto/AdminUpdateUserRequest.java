package bf.evenements.plateforme.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Admin edit of another user's identity (and account type). Status, roles and password have their own endpoints. */
public record AdminUpdateUserRequest(
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Pattern(regexp = "^$|^\\+?[0-9 ]{6,20}$", message = "numero de telephone invalide")
        String phone,
        /** Type de compte (facultatif : inchangé si absent). */
        bf.evenements.plateforme.user.UserType type) {
}
