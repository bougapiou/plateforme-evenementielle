package bf.evenements.plateforme.pointage.dto;

import bf.evenements.plateforme.pointage.PointageStand;
import java.util.UUID;

public record PointageStandResponse(UUID id, String nom) {

    public static PointageStandResponse from(PointageStand s) {
        return new PointageStandResponse(s.getId(), s.getNom());
    }
}
