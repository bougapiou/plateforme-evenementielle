package bf.evenements.plateforme.structure.dto;

import bf.evenements.plateforme.structure.StructureType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StructureRequest(
        @NotBlank @Size(max = 200) String raisonSociale,
        @Size(max = 40) String sigle,
        @NotNull StructureType typeStructure,
        @Size(max = 120) String secteurActivite,
        @Size(max = 60) String rccm,
        @Size(max = 60) String ifu,
        @Size(max = 255) String adresse,
        @Size(max = 120) String ville,
        @Size(max = 120) String pays,
        @Size(max = 30) String telephone,
        @Email @Size(max = 180) String email,
        @Size(max = 255) String siteWeb,
        @Size(max = 500) String logoUrl,
        String description) {
}
