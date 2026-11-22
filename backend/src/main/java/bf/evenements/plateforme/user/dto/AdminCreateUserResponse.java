package bf.evenements.plateforme.user.dto;

/**
 * Compte créé. {@code motDePasseTemporaire} n'est renseigné que si le mot de passe a été
 * généré : il n'est renvoyé qu'une seule fois et n'est jamais stocké en clair.
 */
public record AdminCreateUserResponse(UserResponse user, String motDePasseTemporaire) {
}
