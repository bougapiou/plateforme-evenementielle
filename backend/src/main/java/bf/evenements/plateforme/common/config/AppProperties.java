package bf.evenements.plateforme.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed access to the {@code app.*} configuration tree.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        /** Public base URL of the web front-end, used to build e-mail links. */
        String frontendBaseUrl,
        Cors cors,
        Security security,
        Storage storage,
        Payment payment,
        Bootstrap bootstrap) {

    public String frontendBaseUrl() {
        return frontendBaseUrl == null || frontendBaseUrl.isBlank()
                ? "http://localhost:4200"
                : frontendBaseUrl.replaceAll("/+$", "");
    }

    public record Cors(String allowedOrigins) {
        public String[] originsArray() {
            return allowedOrigins == null || allowedOrigins.isBlank()
                    ? new String[0]
                    : allowedOrigins.split("\\s*,\\s*");
        }
    }

    public record Security(Jwt jwt) {
        public record Jwt(
                String secret,
                Duration accessTokenTtl,
                Duration refreshTokenTtl,
                String issuer) {
        }
    }

    public record Storage(String provider, Local local) {
        public record Local(String basePath, String baseUrl) {
        }
    }

    public record Payment(String provider, String webhookSecret) {
    }

    public record Bootstrap(SuperAdmin superAdmin) {
        public record SuperAdmin(String email, String password, String firstName, String lastName) {
        }
    }
}
