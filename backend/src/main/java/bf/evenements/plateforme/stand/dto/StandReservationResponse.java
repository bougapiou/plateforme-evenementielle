package bf.evenements.plateforme.stand.dto;

import bf.evenements.plateforme.common.money.Money;
import bf.evenements.plateforme.stand.StandReservation;
import bf.evenements.plateforme.stand.StandReservationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StandReservationResponse(
        UUID id,
        String reference,
        String numeroReservation,
        UUID eventId,
        String eventNom,
        UUID standId,
        String standNumero,
        String standTypeNom,
        UUID structureId,
        String structureNom,
        BigDecimal montant,
        String devise,
        String montantFormatte,
        StandReservationStatus statut,
        String informations,
        Instant holdExpireLe,
        Instant dateLimitePaiement,
        Instant payeLe,
        Instant createdAt) {

    public static StandReservationResponse from(StandReservation r) {
        return new StandReservationResponse(
                r.getId(), r.getReference(), r.getNumeroReservation(),
                r.getEvent().getId(), r.getEvent().getNom(),
                r.getStand().getId(), r.getStand().getNumero(), r.getStandType().getNom(),
                r.getStructure() != null ? r.getStructure().getId() : null,
                r.getStructure() != null ? r.getStructure().getRaisonSociale() : null,
                r.getMontant(), r.getDevise(),
                Money.of(r.getMontant(), r.getDevise()).formatted(),
                r.getStatut(), r.getInformations(), r.getHoldExpireLe(),
                r.getDateLimitePaiement(), r.getPayeLe(), r.getCreatedAt());
    }
}
