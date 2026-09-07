package bf.evenements.plateforme.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpeakerRequest(
        @NotBlank @Size(max = 150) String nom,
        @Size(max = 150) String titre,
        @Size(max = 150) String organisation,
        String bio,
        @Size(max = 500) String photoUrl,
        Integer ordre) {
}
