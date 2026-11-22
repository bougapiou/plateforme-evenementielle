package bf.evenements.plateforme.user.dto;

import jakarta.validation.constraints.Size;

/** Nouveau mot de passe défini par un administrateur ; vide = généré automatiquement. */
public record AdminSetPasswordRequest(@Size(max = 72) String password) {
}
