package bf.evenements.plateforme.user.dto;

import bf.evenements.plateforme.user.User;
import bf.evenements.plateforme.user.UserStatus;
import bf.evenements.plateforme.user.UserType;
import java.util.List;
import java.util.UUID;

/** Compact user view embedded in auth responses and listings. */
public record UserSummary(
        UUID id,
        String email,
        String fullName,
        UserType type,
        UserStatus status,
        List<String> roles,
        List<String> permissions) {

    public static UserSummary from(User user) {
        return new UserSummary(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getType(),
                user.getStatus(),
                List.copyOf(user.roleNames()),
                List.copyOf(user.permissionNames()));
    }
}
