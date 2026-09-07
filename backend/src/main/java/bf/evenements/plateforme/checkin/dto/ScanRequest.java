package bf.evenements.plateforme.checkin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ScanRequest(
        @NotBlank String token,
        @NotNull UUID eventId) {
}
