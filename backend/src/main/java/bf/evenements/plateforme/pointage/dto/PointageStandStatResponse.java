package bf.evenements.plateforme.pointage.dto;

import java.util.UUID;

public record PointageStandStatResponse(
        UUID standId,
        String nom,
        long visites,
        long visitesIdentifiees,
        long visitesAnonymes) {
}
