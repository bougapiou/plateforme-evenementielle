package bf.evenements.plateforme.standvisit.dto;

import java.util.List;

/** Public page: visit counts per stand, ranked, plus the event-wide totals. */
public record FrequentationResponse(
        String eventNom,
        long totalVisites,
        long totalIdentifiees,
        long totalAnonymes,
        List<StandFrequentationResponse> stands) {
}
