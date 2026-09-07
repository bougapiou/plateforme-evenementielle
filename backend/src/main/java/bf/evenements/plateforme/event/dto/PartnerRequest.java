package bf.evenements.plateforme.event.dto;

import bf.evenements.plateforme.event.PartnerLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PartnerRequest(
        @NotBlank @Size(max = 150) String nom,
        @Size(max = 500) String logoUrl,
        @Size(max = 255) String siteWeb,
        PartnerLevel niveau,
        Integer ordre) {
}
