package bf.evenements.plateforme.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Payloads of the second-factor steps. */
public final class MfaRequests {

    private MfaRequests() {
    }

    public record Token(@NotBlank String mfaToken) {
    }

    public record Code(@NotBlank String mfaToken,
                       @NotBlank @Pattern(regexp = "^\\s*\\d{3}\\s?\\d{3}\\s*$",
                               message = "code à 6 chiffres attendu") String code) {
    }

    /** Enrolment data to show to the user. */
    public record Setup(String secret, String otpauthUri, String qrDataUri) {
    }
}
