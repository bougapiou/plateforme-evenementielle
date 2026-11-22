package bf.evenements.plateforme.common.security;

import bf.evenements.plateforme.common.config.AppProperties;
import bf.evenements.plateforme.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and validates stateless access tokens (JWT, HS256).
 * Refresh tokens are opaque random strings handled by {@code AuthService}.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final long accessTtlSeconds;

    public JwtService(AppProperties props) {
        AppProperties.Security.Jwt jwt = props.security().jwt();
        this.key = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(jwt.secret()));
        this.issuer = jwt.issuer();
        this.accessTtlSeconds = jwt.accessTokenTtl().getSeconds();
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("type", user.getType().name())
                .claim("roles", List.copyOf(user.roleNames()))
                .claim("permissions", List.copyOf(user.permissionNames()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key)
                .compact();
    }

    /** Lifetime of the intermediate token between the password and the second factor. */
    private static final long MFA_TTL_SECONDS = 300;

    /**
     * Short-lived token proving the password step succeeded, exchanged for real
     * tokens once the second factor is given. {@code purpose} is VERIFY (enrolled
     * user) or SETUP (enrolment still required). Never accepted as an access token.
     */
    public String generateMfaToken(User user, String purpose) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim(MFA_CLAIM, purpose)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(MFA_TTL_SECONDS)))
                .signWith(key)
                .compact();
    }

    /** Verifies an MFA token of the given purpose and returns the user id. */
    public UUID parseMfaToken(String token, String purpose) {
        Claims claims = verify(token);
        if (!purpose.equals(claims.get(MFA_CLAIM, String.class))) {
            throw new io.jsonwebtoken.JwtException("Jeton de double authentification invalide");
        }
        return extractUserId(claims);
    }

    private static final String MFA_CLAIM = "mfa";

    /** Parses and verifies an access token, returning its claims, or throws {@code JwtException}. */
    public Claims parse(String token) {
        Claims claims = verify(token);
        if (claims.get(MFA_CLAIM) != null) {
            throw new io.jsonwebtoken.JwtException("Jeton intermédiaire refusé comme jeton d'accès");
        }
        return claims;
    }

    private Claims verify(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }
}
