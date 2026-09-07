package bf.evenements.plateforme.event.dto;

import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventStatus;
import java.time.Instant;
import java.util.UUID;

/** Compact event card for listings (dashboard and public site). */
public record EventSummary(
        UUID id,
        String nom,
        String sigle,
        String slug,
        String descriptionCourte,
        String categoryNom,
        String logoUrl,
        String coverUrl,
        Instant dateDebut,
        Instant dateFin,
        String ville,
        String lieu,
        String organizerNom,
        boolean standsActifs,
        EventStatus statut) {

    public static EventSummary from(Event e) {
        return new EventSummary(
                e.getId(), e.getNom(), e.getSigle(), e.getSlug(), e.getDescriptionCourte(),
                e.getCategory() != null ? e.getCategory().getNom() : null,
                e.getLogoUrl(), e.getCoverUrl(), e.getDateDebut(), e.getDateFin(),
                e.getVille(), e.getLieu(), e.getOrganizer().getNomAffichage(),
                e.isStandsActifs(), e.getStatut());
    }
}
