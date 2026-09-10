package bf.evenements.plateforme.accreditation.dto;

import bf.evenements.plateforme.accreditation.Accreditation;
import bf.evenements.plateforme.accreditation.AccreditationRole;
import java.time.Instant;
import java.util.UUID;

public record AccreditationResponse(
        UUID id,
        UUID eventId,
        UUID activityId,
        String activiteNom,
        String numero,
        String personneNom,
        String personneEmail,
        String organisation,
        AccreditationRole fonction,
        String fonctionLibelle,
        String photoUrl,
        String statut,
        String qrImageUrl,
        String badgePdfUrl,
        Instant createdAt) {

    public static AccreditationResponse from(Accreditation a) {
        return new AccreditationResponse(
                a.getId(), a.getEvent().getId(),
                a.getActivity() != null ? a.getActivity().getId() : null,
                a.getActivity() != null ? a.getActivity().getTitre() : null,
                a.getNumero(), a.getPersonneNom(), a.getPersonneEmail(), a.getOrganisation(),
                a.getFonction(), a.fonctionLabel(), a.getPhotoUrl(), a.getStatut().name(),
                "/api/accreditations/" + a.getId() + "/qr.png",
                "/api/accreditations/" + a.getId() + "/badge.pdf",
                a.getCreatedAt());
    }
}
