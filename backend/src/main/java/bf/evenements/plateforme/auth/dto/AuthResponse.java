package bf.evenements.plateforme.auth.dto;

import bf.evenements.plateforme.user.dto.UserSummary;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserSummary user) {

    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn,
                                  UserSummary user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
