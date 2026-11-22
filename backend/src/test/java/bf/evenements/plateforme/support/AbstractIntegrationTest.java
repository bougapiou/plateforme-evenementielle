package bf.evenements.plateforme.support;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for HTTP-level integration tests. Uses a single PostgreSQL container
 * shared by every test class (singleton pattern): it is started once and left
 * running for the whole JVM so the cached Spring context stays valid across
 * classes. Ryuk removes the container when the JVM exits.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    public static final PostgreSQLContainer<?> POSTGRES;

    static {
        // Several Spring contexts coexist in the JVM (classes with their own properties),
        // each holding a full Hikari pool. PostgreSQL's default of 100 connections is
        // then exhausted and extra clients (e.g. psql in LoadTestCleanupIT) are refused.
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withCommand("postgres", "-c", "max_connections=300")
                .withReuse(false);
        POSTGRES.start();
    }

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // small pools: tests do not need the production pool size (DB_POOL_SIZE)
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "10");
        // no SMTP server in tests
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "3025");
        // rate limiter off for integration tests (many auth calls from 127.0.0.1)
        registry.add("app.security.rate-limit-per-minute", () -> "0");
        registry.add("app.security.signup-rate-limit-per-minute", () -> "0");
        // second factor is exercised by its own test (MfaIT), not by every login
        registry.add("app.security.mfa.required-roles", () -> "");
        // plain phone lookup kept for the existing tests; the SMS code flow has OtpIT
        registry.add("app.security.ticket-lookup-otp", () -> "false");
        // Tests always exercise the sandbox provider (pay-sandbox/simulate),
        // no matter what a developer's local .env has PAYMENT_PROVIDER set to.
        registry.add("app.payment.provider", () -> "sandbox");
    }

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        RestAssured.basePath = "/";
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }
}
