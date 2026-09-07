package bf.evenements.plateforme.event.dto;

import bf.evenements.plateforme.event.Speaker;
import java.util.UUID;

public record SpeakerResponse(
        UUID id,
        UUID eventId,
        String nom,
        String titre,
        String organisation,
        String bio,
        String photoUrl,
        int ordre) {

    public static SpeakerResponse from(Speaker s) {
        return new SpeakerResponse(s.getId(), s.getEvent().getId(), s.getNom(), s.getTitre(),
                s.getOrganisation(), s.getBio(), s.getPhotoUrl(), s.getOrdre());
    }
}
