package bf.evenements.plateforme.event.dto;

import bf.evenements.plateforme.event.ActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record ActivityRequest(
        @NotBlank @Size(max = 200) String titre,
        String description,
        ActivityType typeActivite,
        @NotNull Instant dateDebut,
        Instant dateFin,
        @Size(max = 120) String salle,
        @Size(max = 200) String lieu,
        @Size(max = 255) String intervenant,
        @Size(max = 255) String moderateur,
        @Size(max = 500) String imageUrl,
        UUID speakerId,
        @PositiveOrZero Integer capacite,
        Integer ordre) {
}
