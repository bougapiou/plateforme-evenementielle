package bf.evenements.plateforme.user.dto;

import bf.evenements.plateforme.user.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Création d'un compte par un administrateur.
 *
 * @param password        mot de passe choisi par l'admin (8 à 72 caractères) ; vide = généré automatiquement
 * @param roles           rôles à attribuer ; vide = rôle par défaut du type de compte
 * @param organisationNom pour un ORGANISATEUR : nom affiché du profil organisateur (actif d'emblée)
 */
public record AdminCreateUserRequest(
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Pattern(regexp = "^$|^\\+?[0-9 ]{6,20}$", message = "numero de telephone invalide")
        String phone,
        UserType type,
        @Size(max = 72) String password,
        Set<String> roles,
        @Size(max = 200) String organisationNom) {
}
