package bf.evenements.plateforme.event.dto;

import bf.evenements.plateforme.event.Event;
import bf.evenements.plateforme.event.EventStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public event page: general info + programme + speakers + partners. */
public record EventPublicResponse(
        UUID id,
        String nom,
        String sigle,
        String slug,
        String descriptionCourte,
        String descriptionDetaillee,
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
        String contactEmail,
        String contactTelephone,
        String siteWeb,
        String conditionsParticipation,
        boolean hasActivities,
        boolean standsActifs,
        boolean standsParticuliers,
        Instant inscriptionDebut,
        Instant inscriptionFin,
        EventStatus statut,
        String organizerNom,
        List<ActivityResponse> programme,
        List<SpeakerResponse> intervenants,
        List<PartnerResponse> partenaires) {

    public static EventPublicResponse from(Event e, List<ActivityResponse> programme,
                                           List<SpeakerResponse> speakers,
                                           List<PartnerResponse> partners) {
        return new EventPublicResponse(
                e.getId(), e.getNom(), e.getSigle(), e.getSlug(), e.getDescriptionCourte(),
                e.getDescriptionDetaillee(),
                e.getCategory() != null ? e.getCategory().getNom() : null,
                e.getLogoUrl(), e.getCoverUrl(), e.getDateDebut(), e.getDateFin(), e.getLieu(),
                e.getAdresse(), e.getVille(), e.getPays(), e.getLatitude(), e.getLongitude(),
                e.getContactEmail(), e.getContactTelephone(), e.getSiteWeb(),
                e.getConditionsParticipation(), e.isHasActivities(), e.isStandsActifs(),
                e.isStandsParticuliers(),
                e.getInscriptionDebut(), e.getInscriptionFin(), e.getStatut(),
                e.getOrganizer().getNomAffichage(), programme, speakers, partners);
    }
}
