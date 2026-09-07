package bf.evenements.plateforme.stand.dto;

import bf.evenements.plateforme.stand.StandType;
import java.math.BigDecimal;
import java.util.UUID;

public record StandTypeResponse(
        UUID id,
        UUID eventId,
        String nom,
        String description,
        String dimensions,
        BigDecimal prixMontant,
        String devise,
        String prixFormatte,
        int quantiteTotale,
        int quantiteReservee,
        int quantiteRestante,
        String equipements,
        String conditions,
        int ordre) {

    public static StandTypeResponse from(StandType t, int reservee) {
        return new StandTypeResponse(
                t.getId(), t.getEvent().getId(), t.getNom(), t.getDescription(), t.getDimensions(),
                t.getPrixMontant(), t.getDevise(), t.price().formatted(), t.getQuantiteTotale(),
                reservee, Math.max(0, t.getQuantiteTotale() - reservee),
                t.getEquipements(), t.getConditions(), t.getOrdre());
    }
}
