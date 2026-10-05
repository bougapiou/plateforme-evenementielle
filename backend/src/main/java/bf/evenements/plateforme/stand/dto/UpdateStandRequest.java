package bf.evenements.plateforme.stand.dto;

import bf.evenements.plateforme.stand.StandStatus;

public record UpdateStandRequest(
        String numero,
        Double positionX,
        Double positionY,
        StandStatus statut) {
}
