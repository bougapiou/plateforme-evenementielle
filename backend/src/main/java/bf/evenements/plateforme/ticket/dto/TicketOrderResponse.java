package bf.evenements.plateforme.ticket.dto;

import bf.evenements.plateforme.common.money.Money;
import bf.evenements.plateforme.ticket.TicketOrder;
import bf.evenements.plateforme.ticket.TicketOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketOrderResponse(
        UUID id,
        String reference,
        UUID eventId,
        String eventNom,
        TicketOrderStatus statut,
        BigDecimal montantTotal,
        String devise,
        String montantFormatte,
        String acheteurNom,
        String acheteurEmail,
        Instant expireLe,
        Instant payeLe,
        Instant createdAt,
        List<LineView> lignes) {

    public record LineView(String ticketNom, int quantite, BigDecimal prixUnitaire) {
    }

    public static TicketOrderResponse from(TicketOrder o) {
        List<LineView> lines = o.getLines().stream()
                .map(l -> new LineView(l.getEventTicket().getNom(), l.getQuantite(), l.getPrixUnitaire()))
                .toList();
        return new TicketOrderResponse(
                o.getId(), o.getReference(), o.getEvent().getId(), o.getEvent().getNom(),
                o.getStatut(), o.getMontantTotal(), o.getDevise(),
                Money.of(o.getMontantTotal(), o.getDevise()).formatted(),
                o.getAcheteurNom(), o.getAcheteurEmail(), o.getExpireLe(), o.getPayeLe(),
                o.getCreatedAt(), lines);
    }
}
