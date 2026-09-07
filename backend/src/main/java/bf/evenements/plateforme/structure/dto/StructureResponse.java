package bf.evenements.plateforme.structure.dto;

import bf.evenements.plateforme.structure.Structure;
import bf.evenements.plateforme.structure.StructureMemberRole;
import bf.evenements.plateforme.structure.StructureStatus;
import bf.evenements.plateforme.structure.StructureType;
import java.time.Instant;
import java.util.UUID;

public record StructureResponse(
        UUID id,
        String raisonSociale,
        String sigle,
        StructureType typeStructure,
        String secteurActivite,
        String rccm,
        String ifu,
        String adresse,
        String ville,
        String pays,
        String telephone,
        String email,
        String siteWeb,
        String logoUrl,
        String description,
        StructureStatus statut,
        UUID ownerId,
        int memberCount,
        StructureMemberRole myRole,
        Instant createdAt) {

    public static StructureResponse from(Structure s, StructureMemberRole myRole) {
        return new StructureResponse(
                s.getId(), s.getRaisonSociale(), s.getSigle(), s.getTypeStructure(),
                s.getSecteurActivite(), s.getRccm(), s.getIfu(), s.getAdresse(), s.getVille(),
                s.getPays(), s.getTelephone(), s.getEmail(), s.getSiteWeb(), s.getLogoUrl(),
                s.getDescription(), s.getStatut(), s.getOwner().getId(), s.getMembers().size(),
                myRole, s.getCreatedAt());
    }
}
