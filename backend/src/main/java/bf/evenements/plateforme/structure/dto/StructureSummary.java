package bf.evenements.plateforme.structure.dto;

import bf.evenements.plateforme.structure.Structure;
import bf.evenements.plateforme.structure.StructureStatus;
import bf.evenements.plateforme.structure.StructureType;
import java.util.UUID;

public record StructureSummary(
        UUID id,
        String raisonSociale,
        String sigle,
        StructureType typeStructure,
        String ville,
        StructureStatus statut) {

    public static StructureSummary from(Structure s) {
        return new StructureSummary(s.getId(), s.getRaisonSociale(), s.getSigle(),
                s.getTypeStructure(), s.getVille(), s.getStatut());
    }
}
