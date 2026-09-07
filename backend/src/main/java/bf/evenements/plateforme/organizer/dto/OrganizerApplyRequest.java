package bf.evenements.plateforme.organizer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** A user's request to be allowed to create events. */
public record OrganizerApplyRequest(
        @NotBlank @Size(max = 200) String nomAffichage,
        @Size(max = 4000) String description,
        UUID structureId,
        @Email @Size(max = 180) String contactEmail,
        @Size(max = 30) String contactTelephone,
        @Size(max = 255) String siteWeb) {
}
