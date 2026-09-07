package bf.evenements.plateforme.organizer.dto;

import bf.evenements.plateforme.organizer.Organizer;
import bf.evenements.plateforme.organizer.OrganizerStatus;
import java.time.Instant;
import java.util.UUID;

public record OrganizerResponse(
        UUID id,
        UUID userId,
        String userFullName,
        String userEmail,
        UUID structureId,
        String structureName,
        String nomAffichage,
        String description,
        String logoUrl,
        String contactEmail,
        String contactTelephone,
        String siteWeb,
        OrganizerStatus statut,
        Instant approuveLe,
        Instant createdAt) {

    public static OrganizerResponse from(Organizer o) {
        return new OrganizerResponse(
                o.getId(),
                o.getUser().getId(),
                o.getUser().getFullName(),
                o.getUser().getEmail(),
                o.getStructure() != null ? o.getStructure().getId() : null,
                o.getStructure() != null ? o.getStructure().getRaisonSociale() : null,
                o.getNomAffichage(),
                o.getDescription(),
                o.getLogoUrl(),
                o.getContactEmail(),
                o.getContactTelephone(),
                o.getSiteWeb(),
                o.getStatut(),
                o.getApprouveLe(),
                o.getCreatedAt());
    }
}
