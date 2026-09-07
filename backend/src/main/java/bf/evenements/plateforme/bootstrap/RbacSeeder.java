package bf.evenements.plateforme.bootstrap;

import bf.evenements.plateforme.rbac.Permission;
import bf.evenements.plateforme.rbac.PermissionRepository;
import bf.evenements.plateforme.rbac.Permissions;
import bf.evenements.plateforme.rbac.Role;
import bf.evenements.plateforme.rbac.RoleNames;
import bf.evenements.plateforme.rbac.RoleRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotently ensures the permission catalogue and the system roles exist.
 * Runs on every startup; only missing rows are created.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RbacSeeder {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    private static final Map<String, Set<String>> SYSTEM_ROLES = Map.of(
            RoleNames.SUPER_ADMIN, Set.copyOf(Permissions.ALL),
            RoleNames.ORGANISATEUR, Set.of(
                    Permissions.EVENT_READ, Permissions.EVENT_CREATE, Permissions.EVENT_UPDATE,
                    Permissions.EVENT_DELETE, Permissions.EVENT_PUBLISH, Permissions.TICKET_MANAGE,
                    Permissions.STAND_MANAGE, Permissions.REGISTRATION_MANAGE, Permissions.PAYMENT_READ,
                    Permissions.CHECKIN_SCAN, Permissions.STATS_OWN_READ, Permissions.STRUCTURE_READ),
            RoleNames.PARTICIPANT, Set.of(
                    Permissions.EVENT_READ, Permissions.TICKET_PURCHASE, Permissions.REGISTRATION_CREATE),
            RoleNames.STRUCTURE, Set.of(
                    Permissions.EVENT_READ, Permissions.TICKET_PURCHASE, Permissions.STAND_RESERVE,
                    Permissions.REGISTRATION_CREATE, Permissions.STRUCTURE_MANAGE),
            RoleNames.PERSONNEL_CONTROLE, Set.of(
                    Permissions.EVENT_READ, Permissions.CHECKIN_SCAN));

    private static final Map<String, String> ROLE_DESCRIPTIONS = Map.of(
            RoleNames.SUPER_ADMIN, "Administrateur de la plateforme (acces total)",
            RoleNames.ORGANISATEUR, "Cree et gere ses propres evenements",
            RoleNames.PARTICIPANT, "Particulier : consultation, inscription, billets",
            RoleNames.STRUCTURE, "Entreprise / institution : inscriptions et reservation de stands",
            RoleNames.PERSONNEL_CONTROLE, "Personnel de controle d'acces (scan des QR codes)");

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    @Transactional
    public void seed() {
        Map<String, Permission> permissions = ensurePermissions();
        SYSTEM_ROLES.forEach((name, perms) -> ensureRole(name, perms, permissions));
        log.info("RBAC seed complete: {} permissions, {} system roles",
                permissions.size(), SYSTEM_ROLES.size());
    }

    private Map<String, Permission> ensurePermissions() {
        for (String name : Permissions.ALL) {
            if (permissionRepository.findByName(name).isEmpty()) {
                permissionRepository.save(new Permission(name, humanize(name)));
            }
        }
        return permissionRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Permission::getName, p -> p));
    }

    private void ensureRole(String name, Set<String> permissionNames, Map<String, Permission> catalogue) {
        Role role = roleRepository.findByName(name).orElseGet(() -> {
            Role r = new Role(name, ROLE_DESCRIPTIONS.getOrDefault(name, name), true);
            return r;
        });
        role.setSystemRole(true);
        // keep system roles authoritative: reset to the declared permission set
        role.getPermissions().clear();
        for (String pName : permissionNames) {
            Permission p = catalogue.get(pName);
            if (p != null) {
                role.addPermission(p);
            }
        }
        roleRepository.save(role);
    }

    private static String humanize(String permission) {
        return permission.toLowerCase().replace('_', ' ');
    }

    /** Exposed for the super-admin seeder. */
    public List<String> systemRoleNames() {
        return List.copyOf(SYSTEM_ROLES.keySet());
    }
}
