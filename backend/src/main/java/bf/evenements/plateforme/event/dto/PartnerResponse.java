package bf.evenements.plateforme.event.dto;

import bf.evenements.plateforme.event.Partner;
import bf.evenements.plateforme.event.PartnerLevel;
import java.util.UUID;

public record PartnerResponse(
        UUID id,
        UUID eventId,
        String nom,
        String logoUrl,
        String siteWeb,
        PartnerLevel niveau,
        int ordre) {

    public static PartnerResponse from(Partner p) {
        return new PartnerResponse(p.getId(), p.getEvent().getId(), p.getNom(), p.getLogoUrl(),
                p.getSiteWeb(), p.getNiveau(), p.getOrdre());
    }
}
