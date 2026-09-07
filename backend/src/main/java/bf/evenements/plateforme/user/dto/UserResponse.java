package bf.evenements.plateforme.user.dto;

import bf.evenements.plateforme.user.User;
import bf.evenements.plateforme.user.UserStatus;
import bf.evenements.plateforme.user.UserType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String phone,
        UserType type,
        UserStatus status,
        List<String> roles,
        List<String> permissions,
        Instant lastLoginAt,
        Instant createdAt) {

    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getFullName(),
                u.getPhone(), u.getType(), u.getStatus(),
                List.copyOf(u.roleNames()), List.copyOf(u.permissionNames()),
                u.getLastLoginAt(), u.getCreatedAt());
    }
}
