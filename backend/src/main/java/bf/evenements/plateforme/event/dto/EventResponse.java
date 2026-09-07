package bf.evenements.plateforme.event.dto;

import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventStatus;
import java.time.Instant;
import java.util.UUID;

/** Full event view for its organiser and administrators. */
public record EventResponse(
        UUID id,
        String nom,
        String sigle,
        String slug,
        String descriptionCourte,
        String descriptionDetaillee,
        UUID categoryId,
        String categoryNom,
        String logoUrl,
        String coverUrl,
        Instant dateDebut,
        Instant dateFin,
        String lieu,
        String adresse,
        String ville,
        String pays,
        Double latitude,
        Double longitude,
        Integer capaciteMax,
        String contactEmail,
        String contactTelephone,
        String siteWeb,
        String conditionsParticipation,
        boolean hasActivities,
        boolean standsActifs,
        boolean validationInscription,
        Instant inscriptionDebut,
        Instant inscriptionFin,
        Instant reservationDebut,
        Instant reservationFin,
        EventStatus statut,
        String motifRefus,
        UUID organizerId,
        String organizerNom,
        Instant soumisLe,
        Instant valideLe,
        Instant publieLe,
        Instant createdAt) {

    public static EventResponse from(Event e) {
        return new EventResponse(
                e.getId(), e.getNom(), e.getSigle(), e.getSlug(), e.getDescriptionCourte(),
                e.getDescriptionDetaillee(),
                e.getCategory() != null ? e.getCategory().getId() : null,
                e.getCategory() != null ? e.getCategory().getNom() : null,
                e.getLogoUrl(), e.getCoverUrl(), e.getDateDebut(), e.getDateFin(), e.getLieu(),
                e.getAdresse(), e.getVille(), e.getPays(), e.getLatitude(), e.getLongitude(),
                e.getCapaciteMax(), e.getContactEmail(), e.getContactTelephone(), e.getSiteWeb(),
                e.getConditionsParticipation(), e.isHasActivities(), e.isStandsActifs(),
                e.isValidationInscription(),
                e.getInscriptionDebut(), e.getInscriptionFin(), e.getReservationDebut(),
                e.getReservationFin(), e.getStatut(), e.getMotifRefus(),
                e.getOrganizer().getId(), e.getOrganizer().getNomAffichage(),
                e.getSoumisLe(), e.getValideLe(), e.getPublieLe(), e.getCreatedAt());
    }
}
