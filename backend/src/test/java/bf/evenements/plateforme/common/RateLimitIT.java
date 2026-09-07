package bf.evenements.plateforme.common;

import static io.restassured.RestAssured.given;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitIT {

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AbstractIntegrationTest.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", AbstractIntegrationTest.POSTGRES::getUsername);
        registry.add("spring.datasource.password", AbstractIntegrationTest.POSTGRES::getPassword);
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "3025");
        registry.add("app.security.rate-limit-per-minute", () -> "5");
    }

    @BeforeEach
    void setup() {
        RestAssured.port = port;
    }

    @Test
    void repeated_failed_logins_are_rate_limited() {
        var body = Map.of("email", "nobody@example.bf", "password", "wrong");
        for (int i = 0; i < 5; i++) {
            given().contentType(ContentType.JSON).body(body)
                    .when().post("/api/auth/login")
                    .then().statusCode(401);
        }
        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/auth/login")
                .then().statusCode(429)
                .body("code", org.hamcrest.Matchers.equalTo("RATE_LIMITED"))
                .header("Retry-After", org.hamcrest.Matchers.notNullValue());
    }
}
