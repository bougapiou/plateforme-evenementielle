package bf.evenements.plateforme.registration.dto;

import bf.evenements.plateforme.registration.RegistrationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record RegisterParticipationRequest(
        @NotNull RegistrationType type,
        UUID structureId,
        @Size(max = 200) String contactNom,
        @Email @Size(max = 180) String contactEmail,
        @Size(max = 30) String contactTelephone,
        String informations,
        @Valid List<ParticipantInput> participants,
        @Valid List<TicketLine> tickets) {

    /**
     * {@code nom} isn't {@code @NotBlank} here: a free ticket category can
     * ask for only the first name (see {@code IdentiteRequise.PRENOM_SEUL}).
     * {@link bf.evenements.plateforme.registration.RegistrationService}
     * checks that at least one of the two is filled.
     */
    public record ParticipantInput(
            @Size(max = 120) String nom,
            @Size(max = 120) String prenom,
            @Email @Size(max = 180) String email,
            @Size(max = 30) String telephone,
            @Size(max = 120) String fonction) {
    }

    public record TicketLine(
            @NotNull UUID eventTicketId,
            @NotNull @Positive Integer quantite) {
    }
}
