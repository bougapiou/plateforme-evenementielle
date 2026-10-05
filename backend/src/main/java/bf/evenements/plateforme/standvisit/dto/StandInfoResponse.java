package bf.evenements.plateforme.standvisit.dto;

import bf.evenements.plateforme.stand.Stand;
import java.util.UUID;

/** What the public "signaler mon passage" page shows about the stand before the visitor submits. */
public record StandInfoResponse(
        UUID id,
        String numero,
        String standTypeNom,
        String exposantNom,
        String eventNom,
        String eventSlug) {

    public static StandInfoResponse from(Stand s, String exposantNom) {
        return new StandInfoResponse(s.getId(), s.getNumero(), s.getStandType().getNom(), exposantNom,
                s.getEvent().getNom(), s.getEvent().getSlug());
    }
}
