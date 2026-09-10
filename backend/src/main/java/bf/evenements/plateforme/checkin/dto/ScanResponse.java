package bf.evenements.plateforme.checkin.dto;

import bf.evenements.plateforme.checkin.CheckinResult;
import java.time.Instant;

public record ScanResponse(
        CheckinResult resultat,
        String message,
        String eventNom,
        String activiteNom,
        String participantNom,
        String categorieNom,
        String numeroBillet,
        Instant heureEntree,
        Instant premierControleLe) {

    public static ScanResponse invalide(String message, String eventNom) {
        return new ScanResponse(CheckinResult.INVALIDE, message, eventNom, null, null, null, null,
                null, null);
    }
}
