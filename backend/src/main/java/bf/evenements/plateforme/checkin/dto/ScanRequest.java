package bf.evenements.plateforme.checkin.dto;

import bf.evenements.plateforme.checkin.CheckinDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ScanRequest(
        @NotBlank String token,
        @NotNull UUID eventId,
        /** When set, the scan controls entry to this activity; null = general entry. */
        UUID activityId,
        /** ENTREE (default) or SORTIE — only used when the event tracks exits. */
        CheckinDirection sens) {

    public CheckinDirection resolvedSens() {
        return sens == null ? CheckinDirection.ENTREE : sens;
    }
}
