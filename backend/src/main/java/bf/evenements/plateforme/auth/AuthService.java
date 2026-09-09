package bf.evenements.plateforme.auth;

import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.auth.dto.AuthResponse;
import bf.evenements.plateforme.auth.dto.CompleteRegistrationRequest;
import bf.evenements.plateforme.auth.dto.GuestSessionRequest;
import bf.evenements.plateforme.auth.dto.LoginRequest;
import bf.evenements.plateforme.auth.dto.RefreshRequest;
import bf.evenements.plateforme.auth.dto.RegisterRequest;
import bf.evenements.plateforme.common.config.AppProperties;
import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ConflictException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.common.security.JwtService;
import bf.evenements.plateforme.common.security.TokenHasher;
import java.util.UUID;
import org.springframework.util.StringUtils;
import bf.evenements.plateforme.rbac.Role;
import bf.evenements.plateforme.rbac.RoleNames;
import bf.evenements.plateforme.rbac.RoleRepository;
import bf.evenements.plateforme.user.User;
import bf.evenements.plateforme.user.UserRepository;
import bf.evenements.plateforme.user.UserStatus;
import bf.evenements.plateforme.user.UserType;
import bf.evenements.plateforme.user.dto.UserSummary;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenHasher tokenHasher;
    private final AuthenticationManager authenticationManager;
    private final AppProperties appProperties;
    private final AuditService auditService;
    private final CurrentUserProvider currentUser;

    @Transactional
    public AuthResponse register(RegisterRequest request, String ip) {
        UserType type = request.resolvedType();
        var existing = userRepository.findByEmailIgnoreCase(request.email());
        if (existing.isPresent()) {
            User u = existing.get();
            if (!u.isGuest()) {
                throw new ConflictException("EMAIL_ALREADY_USED", "Cet e-mail est deja utilise.");
            }
            // A guest checkout account already exists — turn it into a real account.
            u.setPasswordHash(passwordEncoder.encode(request.password()));
            u.setFirstName(request.firstName());
            u.setLastName(request.lastName());
            if (StringUtils.hasText(request.phone())) {
                u.setPhone(request.phone());
            }
            u.setGuest(false);
            u.setStatus(UserStatus.ACTIF);
            addRoleFor(u, type);
            refreshTokenRepository.revokeAllForUser(u);
            auditService.record(u.getId(), u.getEmail(), "AUTH_REGISTER_FROM_GUEST", "User",
                    u.getId().toString(), ip, "type=" + type);
            return issueTokens(u);
        }

        User user = new User();
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone());
        user.setType(type);
        user.setStatus(UserStatus.ACTIF);
        addRoleFor(user, type);
        user = userRepository.save(user);

        auditService.record(user.getId(), user.getEmail(), "AUTH_REGISTER", "User",
                user.getId().toString(), ip, "type=" + type);
        return issueTokens(user);
    }

    /**
     * Opens a session for a visitor who checks out without creating an account.
     * Reuses an existing guest account for the same e-mail; refuses if a real
     * account already exists (the visitor should log in instead).
     */
    @Transactional
    public AuthResponse guestSession(GuestSessionRequest request, String ip) {
        var existing = userRepository.findByEmailIgnoreCase(request.email());
        if (existing.isPresent()) {
            User u = existing.get();
            if (!u.isGuest()) {
                throw new ConflictException("ACCOUNT_EXISTS",
                        "Un compte existe déjà avec cet e-mail. Connectez-vous pour retrouver "
                                + "vos billets et vos inscriptions.");
            }
            if (!StringUtils.hasText(u.getFirstName())) {
                u.setFirstName(request.firstName());
            }
            if (!StringUtils.hasText(u.getLastName())) {
                u.setLastName(request.lastName());
            }
            if (!StringUtils.hasText(u.getPhone()) && StringUtils.hasText(request.phone())) {
                u.setPhone(request.phone());
            }
            return issueTokens(u);
        }

        User user = new User();
        user.setEmail(request.email().toLowerCase());
        // Unusable placeholder — a real password is set when the account is claimed.
        user.setPasswordHash(passwordEncoder.encode("guest-" + UUID.randomUUID()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(StringUtils.hasText(request.phone()) ? request.phone() : null);
        user.setType(UserType.PARTICULIER);
        user.setStatus(UserStatus.ACTIF);
        user.setGuest(true);
        addRoleFor(user, UserType.PARTICULIER);
        user = userRepository.save(user);

        auditService.record(user.getId(), user.getEmail(), "AUTH_GUEST", "User",
                user.getId().toString(), ip, null);
        return issueTokens(user);
    }

    /** Turns the current guest account into a full one by choosing a password. */
    @Transactional
    public AuthResponse completeRegistration(CompleteRegistrationRequest request) {
        User user = userRepository.findById(currentUser.requireId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur courant introuvable"));
        if (!user.isGuest()) {
            throw new BusinessException("NOT_A_GUEST",
                    "Votre compte est déjà actif. Utilisez « changer mon mot de passe ».");
        }
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        if (StringUtils.hasText(request.firstName())) {
            user.setFirstName(request.firstName());
        }
        if (StringUtils.hasText(request.lastName())) {
            user.setLastName(request.lastName());
        }
        user.setGuest(false);
        user.setStatus(UserStatus.ACTIF);
        refreshTokenRepository.revokeAllForUser(user);
        auditService.record(user.getId(), user.getEmail(), "AUTH_ACCOUNT_CLAIMED", "User",
                user.getId().toString(), null, null);
        return issueTokens(user);
    }

    private void addRoleFor(User user, UserType type) {
        String roleName = type == UserType.STRUCTURE ? RoleNames.STRUCTURE : RoleNames.PARTICIPANT;
        if (!user.roleNames().contains(roleName)) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new ResourceNotFoundException("Role systeme manquant: " + roleName));
            user.addRole(role);
        }
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ip) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (BadCredentialsException ex) {
            auditService.record(null, request.email(), "AUTH_LOGIN_FAILED", "User", null, ip, null);
            throw ex;
        }
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BadCredentialsException("Identifiants invalides"));
        user.setLastLoginAt(Instant.now());
        auditService.record(user.getId(), user.getEmail(), "AUTH_LOGIN", "User",
                user.getId().toString(), ip, null);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String hash = tokenHasher.sha256(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Refresh token invalide"));
        if (!stored.isActive()) {
            refreshTokenRepository.revokeAllForUser(stored.getUser());
            throw new BadCredentialsException("Refresh token expire ou revoque");
        }
        stored.setRevoked(true);
        User user = stored.getUser();
        if (!user.isActive()) {
            throw new BadCredentialsException("Compte desactive");
        }
        return issueTokens(user);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        String hash = tokenHasher.sha256(request.refreshToken());
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            auditService.record(token.getUser().getId(), token.getUser().getEmail(),
                    "AUTH_LOGOUT", "User", token.getUser().getId().toString(), null, null);
        });
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefresh = tokenHasher.generateOpaqueToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(tokenHasher.sha256(rawRefresh));
        refreshToken.setExpiresAt(Instant.now().plus(appProperties.security().jwt().refreshTokenTtl()));
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.of(accessToken, rawRefresh, jwtService.getAccessTtlSeconds(),
                UserSummary.from(user));
    }
}
