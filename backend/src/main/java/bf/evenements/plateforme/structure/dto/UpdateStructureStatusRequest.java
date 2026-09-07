package bf.evenements.plateforme.structure.dto;

import bf.evenements.plateforme.structure.StructureStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStructureStatusRequest(@NotNull StructureStatus statut) {
}
