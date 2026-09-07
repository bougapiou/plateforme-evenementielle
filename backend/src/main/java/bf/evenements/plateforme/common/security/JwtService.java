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

    /** Parses and verifies the token, returning its claims, or throws {@code JwtException}. */
    public Claims parse(String token) {
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
