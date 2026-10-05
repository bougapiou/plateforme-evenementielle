package bf.evenements.plateforme.standvisit.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Everything is optional : a visitor may signal their passage with no identity at all. */
public record SignalerPassageRequest(
        @Size(max = 120) String nom,
        @Size(max = 120) String prenom,
        @Pattern(regexp = "^$|^\\+?[0-9 ]{6,20}$", message = "numero de telephone invalide")
        String telephone) {
}
