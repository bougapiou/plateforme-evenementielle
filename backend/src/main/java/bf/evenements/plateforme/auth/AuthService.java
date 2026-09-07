package bf.evenements.plateforme.auth;

import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.auth.dto.AuthResponse;
import bf.evenements.plateforme.auth.dto.LoginRequest;
import bf.evenements.plateforme.auth.dto.RefreshRequest;
import bf.evenements.plateforme.auth.dto.RegisterRequest;
import bf.evenements.plateforme.common.config.AppProperties;
import bf.evenements.plateforme.common.exception.ConflictException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.security.JwtService;
import bf.evenements.plateforme.common.security.TokenHasher;
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

    @Transactional
    public AuthResponse register(RegisterRequest request, String ip) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("EMAIL_ALREADY_USED", "Cet e-mail est deja utilise.");
        }
        UserType type = request.resolvedType();
        String roleName = type == UserType.STRUCTURE ? RoleNames.STRUCTURE : RoleNames.PARTICIPANT;
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role systeme manquant: " + roleName));

        User user = new User();
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone());
        user.setType(type);
        user.setStatus(UserStatus.ACTIF);
        user.addRole(role);
        user = userRepository.save(user);

        auditService.record(user.getId(), user.getEmail(), "AUTH_REGISTER", "User",
                user.getId().toString(), ip, "type=" + type);
        return issueTokens(user);
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
