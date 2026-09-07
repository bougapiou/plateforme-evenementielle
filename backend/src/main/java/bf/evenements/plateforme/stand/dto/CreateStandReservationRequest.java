package bf.evenements.plateforme.stand.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateStandReservationRequest(
        @NotNull UUID eventId,
        @NotNull UUID standId,
        UUID structureId,
        String informations) {
}
