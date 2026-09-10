package bf.evenements.plateforme.checkin.dto;

import bf.evenements.plateforme.checkin.CheckinDirection;
import bf.evenements.plateforme.checkin.CheckinResult;
import java.time.Instant;

public record ScanResponse(
        CheckinResult resultat,
        CheckinDirection sens,
        boolean reentree,
        String message,
        String eventNom,
        String activiteNom,
        String participantNom,
        String categorieNom,
        String numeroBillet,
        Instant heureEntree,
        Instant premierControleLe) {

    public static ScanResponse invalide(String message, String eventNom) {
        return new ScanResponse(CheckinResult.INVALIDE, CheckinDirection.ENTREE, false, message,
                eventNom, null, null, null, null, null, null);
    }

    /** Convenience for the common cases (entry direction, no re-entry flag). */
    public static ScanResponse of(CheckinResult resultat, CheckinDirection sens, String message,
                                  String eventNom, String activiteNom, String participantNom,
                                  String categorieNom, String numeroBillet, Instant heureEntree,
                                  Instant premierControleLe) {
        return new ScanResponse(resultat, sens, false, message, eventNom, activiteNom, participantNom,
                categorieNom, numeroBillet, heureEntree, premierControleLe);
    }
}
