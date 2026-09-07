package bf.evenements.plateforme.user;

import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.common.web.PageResponse;
import bf.evenements.plateforme.rbac.Role;
import bf.evenements.plateforme.rbac.RoleRepository;
import bf.evenements.plateforme.user.dto.ChangePasswordRequest;
import bf.evenements.plateforme.user.dto.UpdateProfileRequest;
import bf.evenements.plateforme.user.dto.UserResponse;
import bf.evenements.plateforme.user.dto.UserSummary;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
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
    public PageResponse<UserSummary> search(String search, UserType type, UserStatus status,
                                            Pageable pageable) {
        var spec = org.springframework.data.jpa.domain.Specification.allOf(
                UserSpecifications.textSearch(search),
                UserSpecifications.hasType(type),
                UserSpecifications.hasStatus(status));
        return PageResponse.of(userRepository.findAll(spec, pageable), UserSummary::from);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return UserResponse.from(loadUser(id));
    }

    @Transactional
    public UserResponse updateStatus(UUID id, UserStatus status) {
        User user = loadUser(id);
        if (user.getId().equals(currentUser.requireId()) && status != UserStatus.ACTIF) {
            throw new BusinessException("SELF_DISABLE_FORBIDDEN",
                    "Vous ne pouvez pas desactiver votre propre compte.");
        }
        user.setStatus(status);
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "USER_STATUS_CHANGED", "User", id.toString(), null, "status=" + status);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateRoles(UUID id, Set<String> roleNames) {
        User user = loadUser(id);
        Set<Role> roles = new LinkedHashSet<>();
        for (String name : roleNames) {
            roles.add(roleRepository.findByName(name)
                    .orElseThrow(() -> new ResourceNotFoundException("Role introuvable: " + name)));
        }
        user.getRoles().clear();
        user.getRoles().addAll(roles);
        auditService.record(currentUser.requireId(), currentUser.require().email(),
                "USER_ROLES_CHANGED", "User", id.toString(), null, "roles=" + roleNames);
        return UserResponse.from(user);
    }

    private User loadCurrent() {
        return loadUser(currentUser.requireId());
    }

    private User loadUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", id));
    }
}
