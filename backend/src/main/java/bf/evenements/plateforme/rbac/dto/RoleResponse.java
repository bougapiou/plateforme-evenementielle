package bf.evenements.plateforme.rbac.dto;

import bf.evenements.plateforme.rbac.Permission;
import bf.evenements.plateforme.rbac.Role;
import java.util.List;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String name,
        String description,
        boolean systemRole,
        List<String> permissions) {

    public static RoleResponse from(Role role) {
        return new RoleResponse(
                role.getId(), role.getName(), role.getDescription(), role.isSystemRole(),
                role.getPermissions().stream().map(Permission::getName).sorted().toList());
    }
}
