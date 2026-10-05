package bf.evenements.plateforme.standvisit.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Bulk variant : the visitor ticks every stand they visited at once, then gives identity (optional) once. */
public record SignalerPassagesRequest(
        @NotEmpty(message = "Sélectionnez au moins un stand.") List<UUID> standIds,
        @Size(max = 120) String nom,
        @Size(max = 120) String prenom,
        @Pattern(regexp = "^$|^\\+?[0-9 ]{6,20}$", message = "numero de telephone invalide")
        String telephone) {
}
