package bf.evenements.plateforme.event.dto;

import bf.evenements.plateforme.event.ActivityFormat;
import bf.evenements.plateforme.event.ActivityAccess;
import bf.evenements.plateforme.event.ActivityType;
import bf.evenements.plateforme.event.EventActivity;
import java.time.Instant;
import java.util.UUID;

public record ActivityResponse(
        UUID id,
        UUID eventId,
        String titre,
        String description,
        ActivityType typeActivite,
        ActivityAccess acces,
        UUID freeTicketId,
        Instant dateDebut,
        Instant dateFin,
        String salle,
        String lieu,
        String intervenant,
        String moderateur,
        String imageUrl,
        UUID speakerId,
        Integer capacite,
        int ordre,
        String theme,
        ActivityFormat format,
        /** Broadcast link — null on public views, see {@link #forManager}. */
        String lienDiffusion) {

    /** Public view: the broadcast link is withheld (only given to registered people). */
    public static ActivityResponse from(EventActivity a) {
        return of(a, null);
    }

    /** Organiser view, including the broadcast link. */
    public static ActivityResponse forManager(EventActivity a) {
        return of(a, a.getLienDiffusion());
    }

    private static ActivityResponse of(EventActivity a, String lien) {
        return new ActivityResponse(
                a.getId(), a.getEvent().getId(), a.getTitre(), a.getDescription(),
                a.getTypeActivite(), a.getAcces(), a.getFreeTicketId(),
                a.getDateDebut(), a.getDateFin(), a.getSalle(), a.getLieu(),
                a.getIntervenant(), a.getModerateur(), a.getImageUrl(), a.getSpeakerId(),
                a.getCapacite(), a.getOrdre(), a.getTheme(), a.getFormat(), lien);
    }
}
