package bf.evenements.plateforme.bootstrap;

import bf.evenements.plateforme.common.config.AppProperties;
import bf.evenements.plateforme.rbac.RoleNames;
import bf.evenements.plateforme.rbac.RoleRepository;
import bf.evenements.plateforme.user.User;
import bf.evenements.plateforme.user.UserRepository;
import bf.evenements.plateforme.user.UserStatus;
import bf.evenements.plateforme.user.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the initial super-administrator account from {@code app.bootstrap.super-admin.*}
 * if no user holds the {@code SUPER_ADMIN} role yet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminSeeder {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    @Transactional
    public void seed() {
        var admin = appProperties.bootstrap().superAdmin();
        if (userRepository.findByEmailIgnoreCase(admin.email()).isPresent()) {
            return;
        }
        var role = roleRepository.findByName(RoleNames.SUPER_ADMIN).orElseThrow(
                () -> new IllegalStateException("Role SUPER_ADMIN non initialise"));

        User user = new User();
        user.setEmail(admin.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(admin.password()));
        user.setFirstName(admin.firstName());
        user.setLastName(admin.lastName());
        user.setType(UserType.ADMIN);
        user.setStatus(UserStatus.ACTIF);
        user.addRole(role);
        userRepository.save(user);
        log.warn("Super-admin cree: {} — pensez a changer le mot de passe par defaut.", admin.email());
    }
}
