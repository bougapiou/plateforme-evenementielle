package bf.evenements.plateforme.pointage.dto;

import java.util.List;

public record PointageStatsResponse(
        long totalVisites,
        long totalIdentifiees,
        long totalAnonymes,
        List<PointageStandStatResponse> stands) {
}
