package bf.evenements.plateforme.ticket.dto;

import bf.evenements.plateforme.ticket.EventTicket;
import bf.evenements.plateforme.ticket.IdentiteRequise;
import bf.evenements.plateforme.ticket.TicketScope;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EventTicketResponse(
        UUID id,
        UUID eventId,
        String nom,
        String description,
        BigDecimal prixMontant,
        String devise,
        String prixFormatte,
        TicketScope portee,
        int quantiteTotale,
        int quantiteVendue,
        int quantiteReservee,
        int quantiteRestante,
        int limiteParUtilisateur,
        Instant venteDebut,
        Instant venteFin,
        boolean actif,
        boolean enVente,
        int ordre,
        boolean formulaireRequis,
        IdentiteRequise identiteRequise,
        List<Map<String, String>> activites) {

    public static EventTicketResponse from(EventTicket t) {
        List<Map<String, String>> acts = t.getActivities().stream()
                .sorted((a, b) -> a.getDateDebut().compareTo(b.getDateDebut()))
                .map(a -> Map.of("id", a.getId().toString(), "titre", a.getTitre()))
                .toList();
        return new EventTicketResponse(
                t.getId(), t.getEvent().getId(), t.getNom(), t.getDescription(),
                t.getPrixMontant(), t.getDevise(), t.price().formatted(), t.getPortee(),
                t.getQuantiteTotale(), t.getQuantiteVendue(), t.getQuantiteReservee(),
                t.quantiteRestante(), t.getLimiteParUtilisateur(), t.getVenteDebut(), t.getVenteFin(),
                t.isActif(), t.onSale(Instant.now()), t.getOrdre(), t.isFormulaireRequis(),
                t.getIdentiteRequise(), acts);
    }

    /** Reduced view for the public site (no internal reservation counters). */
    public static EventTicketResponse publicView(EventTicket t) {
        EventTicketResponse full = from(t);
        return new EventTicketResponse(full.id(), full.eventId(), full.nom(), full.description(),
                full.prixMontant(), full.devise(), full.prixFormatte(), full.portee(),
                full.quantiteTotale(), 0, 0, full.quantiteRestante(), full.limiteParUtilisateur(),
                full.venteDebut(), full.venteFin(), full.actif(), full.enVente(), full.ordre(),
                full.formulaireRequis(), full.identiteRequise(), full.activites());
    }
}
