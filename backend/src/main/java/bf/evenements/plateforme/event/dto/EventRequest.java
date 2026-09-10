package bf.evenements.plateforme.event.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Create / update payload for an event's own fields. */
public record EventRequest(
        @NotBlank @Size(max = 200) String nom,
        @Size(max = 40) String sigle,
        @Size(max = 500) String descriptionCourte,
        String descriptionDetaillee,
        UUID categoryId,
        @Size(max = 500) String logoUrl,
        @Size(max = 500) String coverUrl,
        @NotNull Instant dateDebut,
        @NotNull Instant dateFin,
        @Size(max = 200) String lieu,
        @Size(max = 255) String adresse,
        @Size(max = 120) String ville,
        @Size(max = 120) String pays,
        Double latitude,
        Double longitude,
        @PositiveOrZero Integer capaciteMax,
        @Email @Size(max = 180) String contactEmail,
        @Size(max = 30) String contactTelephone,
        @Size(max = 255) String siteWeb,
        String conditionsParticipation,
        boolean hasActivities,
        boolean standsActifs,
        boolean standsParticuliers,
        boolean validationInscription,
        boolean controleSortie,
        Instant inscriptionDebut,
        Instant inscriptionFin,
        Instant reservationDebut,
        Instant reservationFin) {
}
