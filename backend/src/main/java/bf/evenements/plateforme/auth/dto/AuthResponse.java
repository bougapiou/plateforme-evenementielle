package bf.evenements.plateforme.auth.dto;

import bf.evenements.plateforme.user.dto.UserSummary;

/**
 * Result of an authentication step. Either real tokens, or — when a second
 * factor is needed — {@code mfa} (VERIFY: enter the code; SETUP: enrol an
 * authenticator app first) with a short-lived {@code mfaToken} and no tokens.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserSummary user,
        String mfa,
        String mfaToken) {

    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn,
                                  UserSummary user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user, null, null);
    }

    public static AuthResponse mfa(String purpose, String mfaToken) {
        return new AuthResponse(null, null, null, 0, null, purpose, mfaToken);
    }
}
