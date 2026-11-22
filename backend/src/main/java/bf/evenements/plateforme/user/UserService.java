package bf.evenements.plateforme.user;

import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.auth.RefreshTokenRepository;
import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ConflictException;
import bf.evenements.plateforme.organizer.OrganizerService;
import bf.evenements.plateforme.rbac.Permissions;
import bf.evenements.plateforme.rbac.RoleNames;
import bf.evenements.plateforme.user.dto.AdminCreateUserRequest;
import bf.evenements.plateforme.user.dto.AdminCreateUserResponse;
import bf.evenements.plateforme.user.dto.AdminSetPasswordResponse;
import bf.evenements.plateforme.user.dto.AdminUpdateUserRequest;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.rbac.Permission;
import bf.evenements.plateforme.rbac.Role;
import bf.evenements.plateforme.rbac.RoleRepository;
import bf.evenements.plateforme.user.dto.ChangePasswordRequest;
import bf.evenements.plateforme.user.dto.UpdateProfileRequest;
import bf.evenements.plateforme.user.dto.UserResponse;
import bf.evenements.plateforme.user.dto.UserSummary;
import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUser;
    private final AuditService auditService;
    private final UserDeletionService deletion;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OrganizerService organizerService;

    /** Sans caractères ambigus (0/O, 1/l/I) pour être dicté ou recopié sans erreur. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional(readOnly = true)
    public UserResponse me() {
        return UserResponse.from(loadCurrent());
    }

    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest request) {
        User user = loadCurrent();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone());
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = loadCurrent();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Mot de passe actuel incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        auditService.record(user.getId(), user.getEmail(), "USER_PASSWORD_CHANGED", "User",
                user.getId().toString(), null, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> search(String search, UserType type, UserStatus status, String role,
                                            Pageable pageable) {
        var spec = org.springframework.data.jpa.domain.Specification.allOf(
                UserSpecifications.textSearch(search),
                UserSpecifications.hasType(type),
                UserSpecifications.hasStatus(status),
                UserSpecifications.hasRole(role));
        return PageResponse.of(userRepository.findAll(spec, pageable), UserSummary::from);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return UserResponse.from(loadUser(id));
    }

    // ------------------------------------------------------------------ création par l'admin

    /**
     * Crée un compte. Le mot de passe est celui saisi par l'admin, ou généré (renvoyé une seule
     * fois). Sans rôle précisé, le compte reçoit le rôle par défaut de son type. Attribuer
     * d'autres rôles exige ROLE_MANAGE. Un ORGANISATEUR reçoit un profil organisateur actif.
     */
    @Transactional
    public AdminCreateUserResponse create(AdminCreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_ALREADY_USED", "Cet e-mail est déjà utilisé par un autre compte.");
        }
        UserType type = request.type() == null ? UserType.PARTICULIER : request.type();
        Set<String> roleNames = request.roles() == null || request.roles().isEmpty()
                ? Set.of(defaultRole(type))
                : request.roles();
        if (!roleNames.equals(Set.of(defaultRole(type))) && !currentUser.hasAuthority(Permissions.ROLE_MANAGE)) {
            throw new AccessDeniedException("Attribuer des rôles nécessite la permission « gérer les rôles ».");
        }
        String generated = isBlank(request.password()) ? generatePassword() : null;
        String password = generated != null ? generated : checkPassword(request.password());

        User user = new User();
        user.setEmail(email);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(isBlank(request.phone()) ? null : request.phone().trim());
        user.setType(type);
        user.setStatus(UserStatus.ACTIF);
        user.setGuest(false);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.getRoles().addAll(resolveRoles(roleNames));
        user = userRepository.save(user);

        if (type == UserType.ORGANISATEUR || roleNames.contains(RoleNames.ORGANISATEUR)) {
            organizerService.createActiveForAdmin(user, request.organisationNom());
        }
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "USER_CREATED_BY_ADMIN", "User", user.getId().toString(), null,
                "email=" + email + " type=" + type + " roles=" + roleNames
                        + (generated != null ? " motDePasse=généré" : " motDePasse=saisi"));
        return new AdminCreateUserResponse(UserResponse.from(user), generated);
    }

    /** Nouveau mot de passe défini par l'admin (saisi ou généré) ; ferme toutes les sessions du compte. */
    @Transactional
    public AdminSetPasswordResponse setPassword(UUID id, String password) {
        User user = loadUser(id);
        String generated = isBlank(password) ? generatePassword() : null;
        user.setPasswordHash(passwordEncoder.encode(generated != null ? generated : checkPassword(password)));
        int sessions = refreshTokenRepository.revokeAllForUser(user);
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "USER_PASSWORD_SET_BY_ADMIN", "User", id.toString(), null,
                (generated != null ? "généré" : "saisi") + " ; sessions fermées=" + sessions);
        return new AdminSetPasswordResponse(generated, sessions);
    }

    // ------------------------------------------------------------------ statut / identité / rôles

    @Transactional
    public UserResponse updateStatus(UUID id, UserStatus status) {
        User user = loadUser(id);
        if (user.getId().equals(currentUser.requireId()) && status != UserStatus.ACTIF) {
            throw new BusinessException("SELF_DISABLE_FORBIDDEN",
                    "Vous ne pouvez pas desactiver votre propre compte.");
        }
        if (status != UserStatus.ACTIF) {
            guardLastSuperAdmin(user, "suspendre");
        }
        user.setStatus(status);
        if (status == UserStatus.DESACTIVE) {
            refreshTokenRepository.revokeAllForUser(user); // déconnecté de tous ses appareils
        }
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "USER_STATUS_CHANGED", "User", id.toString(), null, "status=" + status);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse adminUpdate(UUID id, AdminUpdateUserRequest request) {
        User user = loadUser(id);
        String email = request.email().trim().toLowerCase();
        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_ALREADY_USED", "Cet e-mail est déjà utilisé par un autre compte.");
        }
        user.setEmail(email);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone());
        if (request.type() != null) {
            user.setType(request.type());
        }
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "USER_UPDATED_BY_ADMIN", "User", id.toString(), null,
                "email=" + email + (request.type() != null ? " type=" + request.type() : ""));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserDeletionService.History history(UUID id) {
        loadUser(id);
        return deletion.history(id);
    }

    /**
     * Permanently deletes a user. When the account has business history
     * (orders, tickets, registrations, payments…) the call is refused with
     * USER_HAS_HISTORY unless {@code force} is set — the admin is first shown
     * what would be lost. A user who organises events can never be deleted this
     * way: the events (and other people's tickets) must be dealt with first.
     */
    @Transactional
    public void delete(UUID id, boolean force) {
        User user = loadUser(id);
        if (user.getId().equals(currentUser.requireId())) {
            throw new BusinessException("SELF_DELETE_FORBIDDEN",
                    "Vous ne pouvez pas supprimer votre propre compte.");
        }
        guardLastSuperAdmin(user, "supprimer");
        var history = deletion.history(id);
        if (history.any() && !force) {
            throw new ConflictException("USER_HAS_HISTORY",
                    "Ce compte a un historique : " + history.summary()
                            + ". Sa suppression effacera aussi ces données.");
        }
        if (history.evenementsOrganises() > 0) {
            throw new BusinessException("ORGANIZER_HAS_EVENTS",
                    "Ce compte organise " + history.evenementsOrganises()
                            + " événement(s) : supprimez-les d'abord (ou suspendez le compte).");
        }
        String email = user.getEmail();
        deletion.purge(id);
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "USER_DELETED", "User", id.toString(), null,
                "email=" + email + (history.any() ? " ; historique supprimé : " + history.summary() : ""));
    }

    @Transactional
    public UserResponse updateRoles(UUID id, Set<String> roleNames) {
        User user = loadUser(id);
        if (roleNames == null || roleNames.isEmpty()) {
            throw new BusinessException("ROLES_REQUIRED", "Un utilisateur doit avoir au moins un rôle.");
        }
        Set<Role> roles = resolveRoles(roleNames);

        // Garde-fous : ne pas se retirer soi-même la gestion des rôles, ne pas retirer le dernier admin.
        if (user.getId().equals(currentUser.requireId())) {
            boolean keepsRoleManage = roles.stream()
                    .flatMap(r -> r.getPermissions().stream())
                    .map(Permission::getName)
                    .anyMatch(Permissions.ROLE_MANAGE::equals);
            if (!keepsRoleManage) {
                throw new BusinessException("SELF_LOCKOUT",
                        "Vous ne pouvez pas retirer vos propres droits de gestion des rôles.");
            }
        }
        if (user.roleNames().contains(RoleNames.SUPER_ADMIN) && !roleNames.contains(RoleNames.SUPER_ADMIN)) {
            guardLastSuperAdmin(user, "retirer le rôle administrateur à");
        }

        user.getRoles().clear();
        user.getRoles().addAll(roles);
        if (roleNames.contains(RoleNames.ORGANISATEUR)) {
            organizerService.createActiveForAdmin(user, null);
        }
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "USER_ROLES_CHANGED", "User", id.toString(), null, "roles=" + roleNames);
        return UserResponse.from(user);
    }

    // ------------------------------------------------------------------ outils

    /** Refuse de suspendre / supprimer / rétrograder le dernier super-administrateur actif. */
    private void guardLastSuperAdmin(User user, String action) {
        if (user.roleNames().contains(RoleNames.SUPER_ADMIN) && user.isActive()
                && userRepository.countByRoles_NameAndStatus(RoleNames.SUPER_ADMIN, UserStatus.ACTIF) <= 1) {
            throw new BusinessException("LAST_SUPER_ADMIN",
                    "Impossible de " + action + " le dernier administrateur actif de la plateforme.");
        }
    }

    private Set<Role> resolveRoles(Set<String> roleNames) {
        Set<Role> roles = new LinkedHashSet<>();
        for (String name : roleNames) {
            roles.add(roleRepository.findByName(name)
                    .orElseThrow(() -> new ResourceNotFoundException("Role introuvable: " + name)));
        }
        return roles;
    }

    private static String defaultRole(UserType type) {
        return switch (type) {
            case STRUCTURE -> RoleNames.STRUCTURE;
            case ORGANISATEUR -> RoleNames.ORGANISATEUR;
            case PERSONNEL -> RoleNames.PERSONNEL_CONTROLE;
            case ADMIN -> RoleNames.SUPER_ADMIN;
            default -> RoleNames.PARTICIPANT;
        };
    }

    private static String checkPassword(String password) {
        if (password.length() < 8) {
            throw new BusinessException("WEAK_PASSWORD", "Le mot de passe doit contenir au moins 8 caractères.");
        }
        return password;
    }

    /** 12 caractères lisibles, groupés par 4 : « Xk7p-Q2mR-9tWz ». */
    static String generatePassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private User loadCurrent() {
        return loadUser(currentUser.requireId());
    }

    private User loadUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", id));
    }
}
