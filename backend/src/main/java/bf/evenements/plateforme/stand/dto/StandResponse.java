package bf.evenements.plateforme.stand.dto;

import bf.evenements.plateforme.stand.Stand;
import bf.evenements.plateforme.stand.StandStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record StandResponse(
        UUID id,
        UUID eventId,
        UUID standTypeId,
        String standTypeNom,
        BigDecimal prixMontant,
        String prixFormatte,
        String numero,
        Double positionX,
        Double positionY,
        StandStatus statut,
        boolean disponible) {

    public static StandResponse from(Stand s, boolean hasActiveReservation) {
        return new StandResponse(
                s.getId(), s.getEvent().getId(), s.getStandType().getId(),
                s.getStandType().getNom(), s.getStandType().getPrixMontant(),
                s.getStandType().price().formatted(), s.getNumero(),
                s.getPositionX(), s.getPositionY(), s.getStatut(),
                s.getStatut() == StandStatus.DISPONIBLE && !hasActiveReservation);
    }
}
