package bf.evenements.plateforme.user.dto;

/**
 * Résultat d'un changement de mot de passe par l'admin. {@code motDePasseTemporaire} n'est
 * renseigné que pour un mot de passe généré (affiché une seule fois). Les sessions de
 * l'utilisateur ont été fermées sur tous ses appareils.
 */
public record AdminSetPasswordResponse(String motDePasseTemporaire, int sessionsFermees) {
}
