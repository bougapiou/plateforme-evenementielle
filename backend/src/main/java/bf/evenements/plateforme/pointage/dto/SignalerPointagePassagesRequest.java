package bf.evenements.plateforme.pointage.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Le visiteur coche tous les stands visités en une fois, puis donne une identité (facultative) une seule fois. */
public record SignalerPointagePassagesRequest(
        @NotEmpty(message = "Sélectionnez au moins un stand.") List<UUID> standIds,
        @Size(max = 120) String nom,
        @Size(max = 120) String prenom,
        @Pattern(regexp = "^$|^\\+?[0-9 ]{6,20}$", message = "numero de telephone invalide")
        String telephone) {
}
