package bf.evenements.plateforme.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EventCategoryRequest(
        @NotBlank @Size(max = 120) String nom,
        @Size(max = 500) String description,
        @Size(max = 60) String icone,
        Integer ordre,
        Boolean actif) {
}
