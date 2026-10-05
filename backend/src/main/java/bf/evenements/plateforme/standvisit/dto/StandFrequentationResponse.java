package bf.evenements.plateforme.standvisit.dto;

import java.util.UUID;

public record StandFrequentationResponse(
        UUID standId,
        String numero,
        String standTypeNom,
        String exposantNom,
        long visites,
        long visitesIdentifiees,
        long visitesAnonymes) {
}
