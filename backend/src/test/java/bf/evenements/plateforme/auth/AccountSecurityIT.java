package bf.evenements.plateforme.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** SEC-01 (password policy, lockout) and SEC-02 (second factor). */
class AccountSecurityIT extends AbstractIntegrationTest {

    @Autowired
    TotpService totp;

    private static Map<String, String> registration(String email, String password) {
        return Map.of("email", email, "password", password, "firstName", "Awa",
                "lastName", "Sawadogo", "phone", "+226 70 10 20 30");
    }

    @Test
    void weak_passwords_are_refused() {
        for (String weak : new String[] {"12345678", "123456789012", "Azerty123456", "abcdefghijkl", "Court!1"}) {
            given().contentType(ContentType.JSON)
                    .body(registration("faible" + System.nanoTime() + "@example.bf", weak))
                    .when().post("/api/auth/register")
                    .then().statusCode(422)
                    .body("code", equalTo("WEAK_PASSWORD"));
        }
    }

    @Test
    void account_is_locked_after_five_failures() {
        String email = "verrou" + System.nanoTime() + "@example.bf";
        TestAuth.registerAndToken(email, "PARTICULIER");
        for (int i = 0; i < 5; i++) {
            given().contentType(ContentType.JSON)
                    .body(Map.of("email", email, "password", "MauvaisMdp!2026"))
                    .when().post("/api/auth/login")
                    .then().statusCode(401);
        }
        // Even the right password is refused while locked.
        given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "Secret123!Pne"))
                .when().post("/api/auth/login")
                .then().statusCode(423)
                .body("code", equalTo("ACCOUNT_LOCKED"));

        // The admin lifts the lock.
        String userId = given().header("Authorization", "Bearer " + TestAuth.adminToken())
                .when().get("/api/users?search=" + email)
                .then().statusCode(200).extract().path("content[0].id");
        given().header("Authorization", "Bearer " + TestAuth.adminToken())
                .when().post("/api/users/" + userId + "/security/reset")
                .then().statusCode(200);
        TestAuth.login(email, "Secret123!Pne");
    }

    @Test
    void second_factor_is_required_once_enabled() {
        String email = "mfa" + System.nanoTime() + "@example.bf";
        String token = TestAuth.registerAndToken(email, "PARTICULIER");

        String secret = given().header("Authorization", "Bearer " + token)
                .when().post("/api/users/me/mfa/setup")
                .then().statusCode(200)
                .body("qrDataUri", startsWith("data:image/png;base64,"))
                .extract().path("secret");
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Map.of("code", totp.currentCode(secret, Instant.now())))
                .when().post("/api/users/me/mfa/activate")
                .then().statusCode(204);

        // Password alone no longer yields tokens.
        String mfaToken = given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "Secret123!Pne"))
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .body("mfa", equalTo("VERIFY"))
                .body("accessToken", nullValue())
                .extract().path("mfaToken");

        // The intermediate token is not an access token.
        given().header("Authorization", "Bearer " + mfaToken)
                .when().get("/api/users/me")
                .then().statusCode(401);

        given().contentType(ContentType.JSON)
                .body(Map.of("mfaToken", mfaToken, "code", "000000"))
                .when().post("/api/auth/mfa/verify")
                .then().statusCode(401);

        given().contentType(ContentType.JSON)
                .body(Map.of("mfaToken", mfaToken, "code", totp.currentCode(secret, Instant.now())))
                .when().post("/api/auth/mfa/verify")
                .then().statusCode(200)
                .body("accessToken", notNullValue())
                .body("user.email", equalTo(email));
    }

    @Test
    void totp_matches_rfc6238_reference_vector() {
        // RFC 6238 appendix B, SHA1, T = 59 s → 94287082 (last 6 digits: 287082).
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"; // "12345678901234567890"
        org.assertj.core.api.Assertions.assertThat(totp.currentCode(secret, Instant.ofEpochSecond(59)))
                .isEqualTo("287082");
    }
}
