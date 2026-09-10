package bf.evenements.plateforme.accreditation.dto;

import bf.evenements.plateforme.accreditation.AccreditationRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AccreditationRequest(
        @NotBlank @Size(max = 200) String personneNom,
        @Email @Size(max = 180) String personneEmail,
        @Size(max = 200) String organisation,
        @NotNull AccreditationRole fonction,
        @Size(max = 120) String fonctionLibre,
        @Size(max = 500) String photoUrl,
        /** Null = badge valid for the whole event. */
        UUID activityId) {
}
