package bf.evenements.plateforme.standvisit.dto;

import java.util.List;

/** Public page: visit counts per stand, ranked, plus the event-wide total. */
public record FrequentationResponse(
        String eventNom,
        long totalVisites,
        List<StandFrequentationResponse> stands) {
}
