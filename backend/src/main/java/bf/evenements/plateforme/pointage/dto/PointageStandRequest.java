package bf.evenements.plateforme.pointage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin request to create or rename a pointage choice (stand). */
public record PointageStandRequest(
        @NotBlank(message = "Le nom est obligatoire.") @Size(max = 200) String nom) {
}
