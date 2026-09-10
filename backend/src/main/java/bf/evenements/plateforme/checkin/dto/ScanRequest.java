package bf.evenements.plateforme.checkin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ScanRequest(
        @NotBlank String token,
        @NotNull UUID eventId,
        /** When set, the scan controls entry to this activity; null = general entry. */
        UUID activityId) {
}
