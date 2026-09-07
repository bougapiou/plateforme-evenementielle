package bf.evenements.plateforme.rbac;

import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ConflictException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.rbac.dto.RoleRequest;
import bf.evenements.plateforme.rbac.dto.RoleResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll(org.springframework.data.domain.Sort.by("name")).stream()
                .map(RoleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Permission> listPermissions() {
        return permissionRepository.findAll(org.springframework.data.domain.Sort.by("name"));
    }

    @Transactional
    public RoleResponse create(RoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new ConflictException("ROLE_EXISTS", "Un role porte deja ce nom.");
        }
        Role role = new Role(request.name(), request.description(), false);
        applyPermissions(role, request.permissions());
        role = roleRepository.save(role);
        audit("ROLE_CREATED", role);
        return RoleResponse.from(role);
    }

    @Transactional
    public RoleResponse updatePermissions(UUID id, Set<String> permissions) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Role", id));
        if (role.isSystemRole()) {
            throw new BusinessException("SYSTEM_ROLE_LOCKED",
                    "Les permissions d'un role systeme ne sont pas modifiables.");
        }
        role.getPermissions().clear();
        applyPermissions(role, permissions);
        audit("ROLE_PERMISSIONS_UPDATED", role);
        return RoleResponse.from(role);
    }

    private void applyPermissions(Role role, Set<String> names) {
        Set<Permission> resolved = new LinkedHashSet<>();
        for (String name : names) {
            resolved.add(permissionRepository.findByName(name)
                    .orElseThrow(() -> new ResourceNotFoundException("Permission inconnue: " + name)));
        }
        resolved.forEach(role::addPermission);
    }

    private void audit(String action, Role role) {
        auditService.record(currentUser.current().map(u -> u.id()).orElse(null),
                currentUser.current().map(u -> u.email()).orElse(null),
                action, "Role", role.getId().toString(), null, "name=" + role.getName());
    }
}
