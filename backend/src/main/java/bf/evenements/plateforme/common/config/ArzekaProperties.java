package bf.evenements.plateforme.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials and endpoints for the FasoArzeka payment gateway (AvePLUS
 * "Avepay" API). Only read when {@code app.payment.provider=arzeka}; the
 * bundled {@code sandbox} provider never touches this. See
 * {@code .env.example} for the variables — real credentials belong in a
 * local, git-ignored {@code .env} only, never committed.
 */
@ConfigurationProperties(prefix = "arzeka")
public record ArzekaProperties(
        String baseUrl,
        String initiatePaymentUri,
        String checkOrderStatusUri,
        String authUri,
        String merchantId,
        String username,
        String password,
        String grantType,
        /** Base URL Arzeka calls back on (this application's own public URL). */
        String callbackBaseUrl,
        /**
         * Optional static test token (see the API doc's "default token for test"),
         * used instead of {@code /auth/getToken} when set. Never set this in
         * production — it bypasses the real login and expires like any JWT.
         */
        String testStaticToken) {
}
