package bf.evenements.plateforme.auth;

import bf.evenements.plateforme.audit.AuditService;
import bf.evenements.plateforme.auth.dto.AuthResponse;
import bf.evenements.plateforme.auth.dto.CompleteRegistrationRequest;
import bf.evenements.plateforme.auth.dto.ForgotPasswordRequest;
import bf.evenements.plateforme.auth.dto.GuestSessionRequest;
import bf.evenements.plateforme.auth.dto.LoginRequest;
import bf.evenements.plateforme.auth.dto.RefreshRequest;
import bf.evenements.plateforme.auth.dto.RegisterRequest;
import bf.evenements.plateforme.auth.dto.ResetPasswordRequest;
import bf.evenements.plateforme.common.config.AppProperties;
import bf.evenements.plateforme.common.exception.BusinessException;
import bf.evenements.plateforme.common.exception.ConflictException;
import bf.evenements.plateforme.common.exception.ResourceNotFoundException;
import bf.evenements.plateforme.common.security.CurrentUserProvider;
import bf.evenements.plateforme.common.security.JwtService;
import bf.evenements.plateforme.common.security.TokenHasher;
import bf.evenements.plateforme.notification.EmailSender;
import java.time.Duration;
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

    /** How long a password-reset link stays valid. */
    private static final Duration RESET_TOKEN_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final bf.evenements.plateforme.ticket.TicketRepository ticketRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenHasher tokenHasher;
    private final AuthenticationManager authenticationManager;
    private final AppProperties appProperties;
    private final AuditService auditService;
    private final CurrentUserProvider currentUser;
    private final EmailSender emailSender;
    private final bf.evenements.plateforme.common.security.PasswordPolicy passwordPolicy;
    private final LoginAttemptService loginAttempts;
    private final TotpService totp;
    private final OtpService otpService;
    private final bf.evenements.plateforme.notification.SmsSender smsSender;

    /** « Retrouver mon billet » requires a code received by SMS (PART-04). */
    @org.springframework.beans.factory.annotation.Value("${app.security.ticket-lookup-otp:true}")
    private boolean ticketLookupOtp = true;

    /** Roles whose holders must use a second factor (empty = never required). */
    @org.springframework.beans.factory.annotation.Value("${app.security.mfa.required-roles:SUPER_ADMIN,ORGANISATEUR}")
    private java.util.List<String> mfaRequiredRoles = java.util.List.of();

    private static final String MFA_ISSUER = "PNE Événements";

    @Transactional
    public AuthResponse register(RegisterRequest request, String ip) {
        UserType type = request.resolvedType();
        passwordPolicy.check(request.password(), request.email());
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

    /** Non-routable domain for guests who checked out with a phone but no e-mail. */
    public static final String PLACEHOLDER_EMAIL_DOMAIN = "@guest.plateforme.local";

    /**
     * Opens a session for a visitor who checks out without creating an account.
     * A phone number is required; the e-mail is optional. Reuses an existing guest
     * (matched by e-mail, else by phone); refuses if a real account already exists.
     */
    @Transactional
    public AuthResponse guestSession(GuestSessionRequest request, String ip) {
        if (!StringUtils.hasText(request.firstName()) && !StringUtils.hasText(request.lastName())) {
            throw new BusinessException("IDENTITY_REQUIRED",
                    "Renseignez au moins votre nom ou votre prénom.");
        }
        // Neither is @NotBlank on the DTO (one alone is allowed) — but the
        // entity column is NOT NULL, so a missing one becomes "" rather than null.
        String firstName = StringUtils.hasText(request.firstName()) ? request.firstName() : "";
        String lastName = StringUtils.hasText(request.lastName()) ? request.lastName() : "";

        var existing = StringUtils.hasText(request.email())
                ? userRepository.findByEmailIgnoreCase(request.email())
                : userRepository.findFirstByPhoneAndGuestTrueOrderByCreatedAtDesc(request.phone());
        if (existing.isPresent()) {
            User u = existing.get();
            if (!u.isGuest()) {
                throw new ConflictException("ACCOUNT_EXISTS",
                        "Un compte existe déjà avec cet e-mail. Connectez-vous pour retrouver "
                                + "vos billets et vos inscriptions.");
            }
            if (!StringUtils.hasText(u.getFirstName())) {
                u.setFirstName(firstName);
            }
            if (!StringUtils.hasText(u.getLastName())) {
                u.setLastName(lastName);
            }
            u.setPhone(request.phone());
            return issueTokens(u);
        }

        User user = new User();
        user.setEmail(StringUtils.hasText(request.email())
                ? request.email().toLowerCase()
                : "tel-" + digitsOnly(request.phone()) + PLACEHOLDER_EMAIL_DOMAIN);
        // Unusable placeholder — a real password is set when the account is claimed.
        user.setPasswordHash(passwordEncoder.encode("guest-" + UUID.randomUUID()));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhone(request.phone());
        user.setType(UserType.PARTICULIER);
        user.setStatus(UserStatus.ACTIF);
        user.setGuest(true);
        addRoleFor(user, UserType.PARTICULIER);
        user = userRepository.save(user);

        auditService.record(user.getId(), user.getEmail(), "AUTH_GUEST", "User",
                user.getId().toString(), ip, null);
        return issueTokens(user);
    }

    /**
     * Same as {@link #guestSession}, but for a visitor claiming a "no form
     * needed" free ticket: only the phone number is real, the name is a
     * placeholder (the organizer explicitly opted out of collecting one).
     */
    @Transactional
    public AuthResponse guestSessionQuick(String phone, String ip) {
        return guestSession(new GuestSessionRequest(null, "Visiteur", "", phone), ip);
    }

    /**
     * Guest account holding tickets issued at the door for a visitor (phone +
     * name): reuses the latest guest with this phone, else creates one. No
     * session is opened.
     */
    @Transactional
    public User guestUserFor(String phone, String nom) {
        return userRepository.findFirstByPhoneAndGuestTrueOrderByCreatedAtDesc(phone).orElseGet(() -> {
            User user = new User();
            user.setEmail("tel-" + digitsOnly(phone) + "-" + UUID.randomUUID().toString().substring(0, 8)
                    + PLACEHOLDER_EMAIL_DOMAIN);
            user.setPasswordHash(passwordEncoder.encode("guest-" + UUID.randomUUID()));
            user.setFirstName("");
            user.setLastName(nom == null ? "" : nom);
            user.setPhone(phone);
            user.setType(UserType.PARTICULIER);
            user.setStatus(UserStatus.ACTIF);
            user.setGuest(true);
            addRoleFor(user, UserType.PARTICULIER);
            return userRepository.save(user);
        });
    }

    /**
     * "Retrouver mon billet": opens a session only for an EXISTING guest with
     * this phone who actually holds tickets. Never creates an account.
     */
    @Transactional
    public AuthResponse guestSessionForTickets(String phone, String ip) {
        if (ticketLookupOtp) {
            throw new BusinessException("OTP_REQUIRED",
                    "Un code de vérification envoyé par SMS est nécessaire pour retrouver vos billets.");
        }
        return openTicketHolderSession(phone, ip);
    }

    /**
     * « Mes billets » without password, step 1: sends a 6-digit code by SMS when
     * this number holds tickets. Answers the same way otherwise (no disclosure).
     */
    @Transactional(readOnly = true)
    public void requestTicketOtp(String phone, String ip) {
        boolean holder = userRepository.findFirstByPhoneAndGuestTrueOrderByCreatedAtDesc(phone)
                .filter(u -> ticketRepository.existsByOrderUserId(u.getId()))
                .isPresent();
        if (!holder) {
            return;
        }
        String code = otpService.issue(phone);
        smsSender.send(phone, "PNE : votre code pour retrouver vos billets est " + code
                + ". Il expire dans 10 minutes. Ne le communiquez à personne.");
        auditService.record(null, null, "AUTH_OTP_SENT", "User", null, ip, null);
    }

    /** Step 2: the code opens a session on the guest account holding the tickets. */
    @Transactional
    public AuthResponse verifyTicketOtp(String phone, String code, String ip) {
        if (!otpService.consume(phone, code)) {
            throw new BusinessException("OTP_INVALID", "Code incorrect ou expiré. Demandez un nouveau code.");
        }
        return openTicketHolderSession(phone, ip);
    }

    private AuthResponse openTicketHolderSession(String phone, String ip) {
        User user = userRepository.findFirstByPhoneAndGuestTrueOrderByCreatedAtDesc(phone)
                .filter(u -> ticketRepository.existsByOrderUserId(u.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun billet n'est associé à ce numéro de téléphone."));
        auditService.record(user.getId(), user.getEmail(), "AUTH_GUEST_LOOKUP", "User",
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

        // A phone-only guest must supply a real e-mail to become a login-able account.
        if (user.getEmail().endsWith(PLACEHOLDER_EMAIL_DOMAIN)) {
            if (!StringUtils.hasText(request.email())) {
                throw new BusinessException("EMAIL_REQUIRED",
                        "Ajoutez une adresse e-mail pour créer votre compte.");
            }
            if (userRepository.existsByEmailIgnoreCase(request.email())) {
                throw new ConflictException("EMAIL_ALREADY_USED",
                        "Cet e-mail est déjà utilisé par un autre compte.");
            }
            user.setEmail(request.email().toLowerCase());
        }

        passwordPolicy.check(request.password(), user.getEmail());
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

    private static String digitsOnly(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }

    /**
     * Starts a password-reset flow. Always succeeds silently — the response must
     * not reveal whether the e-mail is registered. Guest accounts (no password
     * chosen yet) are ignored: they claim their account instead.
     */
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request, String ip) {
        userRepository.findByEmailIgnoreCase(request.email()).ifPresent(user -> {
            if (user.isGuest()) {
                return;
            }
            Instant now = Instant.now();
            passwordResetTokenRepository.invalidateAllForUser(user, now);

            String rawToken = tokenHasher.generateOpaqueToken();
            PasswordResetToken token = new PasswordResetToken();
            token.setUser(user);
            token.setTokenHash(tokenHasher.sha256(rawToken));
            token.setExpiresAt(now.plus(RESET_TOKEN_TTL));
            passwordResetTokenRepository.save(token);

            String link = appProperties.frontendBaseUrl()
                    + "/mot-de-passe/reinitialiser?token=" + rawToken;
            emailSender.send(user.getEmail(), "Réinitialisation de votre mot de passe",
                    "Bonjour " + user.getFirstName() + ",\n\n"
                            + "Vous avez demandé à réinitialiser le mot de passe de votre compte "
                            + "sur la Plateforme Nationale de Gestion des Événements.\n\n"
                            + "Ouvrez ce lien pour choisir un nouveau mot de passe (valable 1 heure) :\n"
                            + link + "\n\n"
                            + "Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail : "
                            + "votre mot de passe reste inchangé.");
            auditService.record(user.getId(), user.getEmail(), "AUTH_PASSWORD_RESET_REQUESTED",
                    "User", user.getId().toString(), ip, null);
        });
    }

    /** Consumes a reset token and sets a new password, ending all other sessions. */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHash(tokenHasher.sha256(request.token().trim()))
                .filter(PasswordResetToken::isUsable)
                .orElseThrow(() -> new BusinessException("RESET_TOKEN_INVALID",
                        "Ce lien de réinitialisation est invalide ou a expiré. "
                                + "Demandez-en un nouveau."));

        User user = token.getUser();
        passwordPolicy.check(request.password(), user.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIF);
        user.setEchecsConnexion(0);
        user.setVerrouilleJusqu(null);
        token.setUsedAt(Instant.now());
        refreshTokenRepository.revokeAllForUser(user);
        auditService.record(user.getId(), user.getEmail(), "AUTH_PASSWORD_RESET", "User",
                user.getId().toString(), null, null);
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
        userRepository.findByEmailIgnoreCase(request.email()).ifPresent(this::ensureNotLocked);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (BadCredentialsException ex) {
            auditService.record(null, request.email(), "AUTH_LOGIN_FAILED", "User", null, ip, null);
            if (loginAttempts.recordFailure(request.email())) {
                auditService.record(null, request.email(), "AUTH_ACCOUNT_LOCKED", "User", null, ip, null);
            }
            throw ex;
        }
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BadCredentialsException("Identifiants invalides"));
        user.setEchecsConnexion(0);
        user.setVerrouilleJusqu(null);

        if (user.isTotpActif()) {
            auditService.record(user.getId(), user.getEmail(), "AUTH_PASSWORD_OK_MFA_PENDING", "User",
                    user.getId().toString(), ip, null);
            return AuthResponse.mfa("VERIFY", jwtService.generateMfaToken(user, "VERIFY"));
        }
        if (requiresMfa(user)) {
            return AuthResponse.mfa("SETUP", jwtService.generateMfaToken(user, "SETUP"));
        }
        return completeLogin(user, ip);
    }

    private AuthResponse completeLogin(User user, String ip) {
        user.setLastLoginAt(Instant.now());
        auditService.record(user.getId(), user.getEmail(), "AUTH_LOGIN", "User",
                user.getId().toString(), ip, null);
        return issueTokens(user);
    }

    private void ensureNotLocked(User user) {
        Instant until = user.getVerrouilleJusqu();
        if (until != null && until.isAfter(Instant.now())) {
            long minutes = Math.max(1, Duration.between(Instant.now(), until).toMinutes() + 1);
            throw new bf.evenements.plateforme.common.exception.AccountLockedException(
                    "Compte verrouillé après plusieurs échecs de connexion. Réessayez dans "
                            + minutes + " minute" + (minutes > 1 ? "s" : "") + ".");
        }
    }

    /** Whether this user's roles make a second factor mandatory. */
    public boolean requiresMfa(User user) {
        return !user.isGuest() && mfaRequiredRoles.stream()
                .filter(StringUtils::hasText).map(String::trim)
                .anyMatch(r -> user.roleNames().contains(r));
    }

    // ------------------------------------------------------------ second factor

    /** Second step of the log-in for an enrolled user. */
    @Transactional
    public AuthResponse verifyMfa(String mfaToken, String code, String ip) {
        User user = mfaUser(mfaToken, "VERIFY");
        ensureNotLocked(user);
        if (!totp.verify(user.getTotpSecret(), code)) {
            mfaFailure(user, ip);
        }
        user.setEchecsConnexion(0);
        return completeLogin(user, ip);
    }

    /** Enrolment, step 1: a fresh secret to scan in the authenticator app. */
    @Transactional
    public bf.evenements.plateforme.auth.dto.MfaRequests.Setup setupMfa(String mfaToken) {
        User user = mfaUser(mfaToken, "SETUP");
        return newSecretFor(user);
    }

    /** Enrolment, step 2: the first code proves the app is set up; the session opens. */
    @Transactional
    public AuthResponse activateMfa(String mfaToken, String code, String ip) {
        User user = mfaUser(mfaToken, "SETUP");
        ensureNotLocked(user);
        if (user.getTotpSecret() == null || !totp.verify(user.getTotpSecret(), code)) {
            mfaFailure(user, ip);
        }
        user.setTotpActif(true);
        user.setEchecsConnexion(0);
        auditService.record(user.getId(), user.getEmail(), "AUTH_MFA_ENABLED", "User",
                user.getId().toString(), ip, null);
        return completeLogin(user, ip);
    }

    /** Voluntary enrolment from the profile page (any full account). */
    @Transactional
    public bf.evenements.plateforme.auth.dto.MfaRequests.Setup setupMfaForCurrentUser() {
        User user = currentFullUser();
        if (user.isTotpActif()) {
            throw new BusinessException("MFA_ALREADY_ENABLED", "La double authentification est déjà active.");
        }
        return newSecretFor(user);
    }

    @Transactional
    public void activateMfaForCurrentUser(String code, String ip) {
        User user = currentFullUser();
        if (user.getTotpSecret() == null || !totp.verify(user.getTotpSecret(), code)) {
            throw new BusinessException("MFA_CODE_INVALID", "Code incorrect. Vérifiez l'heure de votre téléphone.");
        }
        user.setTotpActif(true);
        auditService.record(user.getId(), user.getEmail(), "AUTH_MFA_ENABLED", "User",
                user.getId().toString(), ip, null);
    }

    private User currentFullUser() {
        User user = userRepository.findById(currentUser.requireId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur courant introuvable"));
        if (user.isGuest()) {
            throw new BusinessException("NOT_A_FULL_ACCOUNT", "Créez d'abord votre compte.");
        }
        return user;
    }

    private bf.evenements.plateforme.auth.dto.MfaRequests.Setup newSecretFor(User user) {
        String secret = totp.newSecret();
        user.setTotpSecret(secret);
        user.setTotpActif(false);
        String uri = totp.otpauthUri(secret, user.getEmail(), MFA_ISSUER);
        String qr = "data:image/png;base64," + java.util.Base64.getEncoder()
                .encodeToString(bf.evenements.plateforme.common.web.QrImages.png(uri, 240));
        return new bf.evenements.plateforme.auth.dto.MfaRequests.Setup(secret, uri, qr);
    }

    private User mfaUser(String mfaToken, String purpose) {
        UUID id;
        try {
            id = jwtService.parseMfaToken(mfaToken, purpose);
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            throw new BusinessException("MFA_TOKEN_EXPIRED",
                    "Session de connexion expirée. Saisissez à nouveau votre mot de passe.");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BadCredentialsException("Identifiants invalides"));
        if (!user.isActive()) {
            throw new BadCredentialsException("Compte desactive");
        }
        return user;
    }

    private void mfaFailure(User user, String ip) {
        auditService.record(user.getId(), user.getEmail(), "AUTH_MFA_FAILED", "User",
                user.getId().toString(), ip, null);
        if (loginAttempts.recordFailure(user.getEmail())) {
            auditService.record(user.getId(), user.getEmail(), "AUTH_ACCOUNT_LOCKED", "User",
                    user.getId().toString(), ip, null);
        }
        throw new BadCredentialsException("Code de vérification incorrect");
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
