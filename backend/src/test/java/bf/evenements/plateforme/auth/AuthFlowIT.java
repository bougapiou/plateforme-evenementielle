package bf.evenements.plateforme.auth;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import bf.evenements.plateforme.notification.EmailSender;
import bf.evenements.plateforme.support.AbstractIntegrationTest;
import io.restassured.http.ContentType;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;

class AuthFlowIT extends AbstractIntegrationTest {

    @MockBean
    EmailSender emailSender;

    /** Runs {@code POST /auth/password/forgot} and returns the raw token from the e-mailed link. */
    private String requestResetToken(String email) {
        given().contentType(ContentType.JSON).body(Map.of("email", email))
                .when().post("/api/auth/password/forgot").then().statusCode(202);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq(email), anyString(), body.capture());
        Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(body.getValue());
        assertThat(m.find()).as("reset link contains a token").isTrue();
        return m.group(1);
    }

    @Test
    void register_login_refresh_logout_and_protected_access() {
        String email = "amina" + System.nanoTime() + "@example.bf";

        // --- register ---
        String refreshToken = given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "email", email,
                        "password", "Secret123",
                        "firstName", "Amina",
                        "lastName", "Ouedraogo",
                        "phone", "+226 70 11 22 33"))
                .when().post("/api/auth/register")
                .then().statusCode(201)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue())
                .body("user.roles", hasItem("PARTICIPANT"))
                .extract().path("refreshToken");

        // --- login ---
        var login = given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "Secret123"))
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .body("tokenType", equalTo("Bearer"))
                .extract().response();
        String accessToken = login.path("accessToken");
        refreshToken = login.path("refreshToken");

        // --- protected endpoint with token ---
        given().header("Authorization", "Bearer " + accessToken)
                .when().get("/api/users/me")
                .then().statusCode(200)
                .body("email", equalTo(email));

        // --- protected endpoint without token -> 401 ---
        given().when().get("/api/users/me").then().statusCode(401);

        // --- insufficient role -> 403 ---
        given().header("Authorization", "Bearer " + accessToken)
                .when().get("/api/users")
                .then().statusCode(403);

        // --- refresh rotates the token ---
        String newRefresh = given()
                .contentType(ContentType.JSON)
                .body(Map.of("refreshToken", refreshToken))
                .when().post("/api/auth/refresh")
                .then().statusCode(200)
                .body("accessToken", notNullValue())
                .extract().path("refreshToken");

        // old refresh token no longer valid
        given().contentType(ContentType.JSON)
                .body(Map.of("refreshToken", refreshToken))
                .when().post("/api/auth/refresh")
                .then().statusCode(401);

        // --- logout invalidates the refresh token ---
        given().contentType(ContentType.JSON)
                .body(Map.of("refreshToken", newRefresh))
                .when().post("/api/auth/logout")
                .then().statusCode(204);

        given().contentType(ContentType.JSON)
                .body(Map.of("refreshToken", newRefresh))
                .when().post("/api/auth/refresh")
                .then().statusCode(401);
    }

    @Test
    void duplicate_email_is_rejected() {
        String email = "dup" + System.nanoTime() + "@example.bf";
        Map<String, String> body = Map.of(
                "email", email, "password", "Secret123",
                "firstName", "Paul", "lastName", "Kabore", "phone", "+226 70 44 55 66");

        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/auth/register").then().statusCode(201);

        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/auth/register")
                .then().statusCode(409)
                .body("code", equalTo("EMAIL_ALREADY_USED"));
    }

    @Test
    void guest_session_can_be_claimed_as_a_full_account() {
        String email = "invite" + System.nanoTime() + "@example.bf";

        // --- guest session, no password ---
        String guestToken = given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "firstName", "Awa", "lastName", "Invitee",
                        "phone", "+226 70 77 88 99"))
                .when().post("/api/auth/guest")
                .then().statusCode(200)
                .body("user.guest", equalTo(true))
                .body("user.roles", hasItem("PARTICIPANT"))
                .extract().path("accessToken");

        // a guest cannot log in (no password chosen)
        given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "whatever"))
                .when().post("/api/auth/login").then().statusCode(401);

        // --- claim the account (the e-mail was given, so no extra e-mail needed) ---
        given().header("Authorization", "Bearer " + guestToken)
                .contentType(ContentType.JSON)
                .body(Map.of("password", "MonMotDePasse!2026"))
                .when().post("/api/auth/complete")
                .then().statusCode(200)
                .body("user.guest", equalTo(false));

        // now login works
        given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "MonMotDePasse!2026"))
                .when().post("/api/auth/login").then().statusCode(200);

        // a fresh guest session on that (now real) e-mail is refused
        given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "firstName", "X", "lastName", "Y",
                        "phone", "+226 71 00 00 00"))
                .when().post("/api/auth/guest")
                .then().statusCode(409).body("code", equalTo("ACCOUNT_EXISTS"));

        // claiming an already-active account is refused
        String realToken = given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "MonMotDePasse!2026"))
                .when().post("/api/auth/login").then().statusCode(200).extract().path("accessToken");
        given().header("Authorization", "Bearer " + realToken)
                .contentType(ContentType.JSON).body(Map.of("password", "Autre!2026"))
                .when().post("/api/auth/complete")
                .then().statusCode(422).body("code", equalTo("NOT_A_GUEST"));
    }

    @Test
    void register_upgrades_an_existing_guest_account() {
        String email = "guest2reg" + System.nanoTime() + "@example.bf";
        given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "firstName", "B", "lastName", "C",
                        "phone", "+226 72 00 00 00"))
                .when().post("/api/auth/guest").then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "Passw0rd!2026",
                        "firstName", "B", "lastName", "C", "phone", "+226 72 00 00 00"))
                .when().post("/api/auth/register")
                .then().statusCode(201)
                .body("user.guest", equalTo(false));
    }

    @Test
    void phone_only_guest_must_add_an_email_to_claim_the_account() {
        String phone = "+226 73 " + (System.nanoTime() % 100000000);

        // guest checkout with a phone but no e-mail
        String guestToken = given().contentType(ContentType.JSON)
                .body(Map.of("firstName", "Sié", "lastName", "Palé", "phone", phone))
                .when().post("/api/auth/guest")
                .then().statusCode(200).body("user.guest", equalTo(true))
                .extract().path("accessToken");

        // the same phone reuses the same guest session
        given().contentType(ContentType.JSON)
                .body(Map.of("firstName", "Sié", "lastName", "Palé", "phone", phone))
                .when().post("/api/auth/guest").then().statusCode(200);

        // claiming without an e-mail is refused
        given().header("Authorization", "Bearer " + guestToken).contentType(ContentType.JSON)
                .body(Map.of("password", "MotDePasse!2026"))
                .when().post("/api/auth/complete")
                .then().statusCode(422).body("code", equalTo("EMAIL_REQUIRED"));

        // with an e-mail it works, and login by that e-mail works
        String realEmail = "sie" + System.nanoTime() + "@example.bf";
        given().header("Authorization", "Bearer " + guestToken).contentType(ContentType.JSON)
                .body(Map.of("password", "MotDePasse!2026", "email", realEmail))
                .when().post("/api/auth/complete")
                .then().statusCode(200).body("user.guest", equalTo(false));
        given().contentType(ContentType.JSON)
                .body(Map.of("email", realEmail, "password", "MotDePasse!2026"))
                .when().post("/api/auth/login").then().statusCode(200);
    }

    @Test
    void forgot_then_reset_password_ends_old_sessions() {
        String email = "oubli" + System.nanoTime() + "@example.bf";
        String oldRefresh = given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "Ancien!2026",
                        "firstName", "Fatou", "lastName", "Sawadogo", "phone", "+226 74 00 00 00"))
                .when().post("/api/auth/register").then().statusCode(201)
                .extract().path("refreshToken");

        String token = requestResetToken(email);

        given().contentType(ContentType.JSON)
                .body(Map.of("token", token, "password", "Nouveau!2026"))
                .when().post("/api/auth/password/reset").then().statusCode(204);

        // new password works, old one does not
        given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "Nouveau!2026"))
                .when().post("/api/auth/login").then().statusCode(200);
        given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "Ancien!2026"))
                .when().post("/api/auth/login").then().statusCode(401);

        // sessions opened before the reset are revoked
        given().contentType(ContentType.JSON).body(Map.of("refreshToken", oldRefresh))
                .when().post("/api/auth/refresh").then().statusCode(401);

        // the token cannot be reused
        given().contentType(ContentType.JSON)
                .body(Map.of("token", token, "password", "Encore!2026"))
                .when().post("/api/auth/password/reset")
                .then().statusCode(422).body("code", equalTo("RESET_TOKEN_INVALID"));
    }

    @Test
    void forgot_for_an_unknown_email_is_silently_accepted() {
        given().contentType(ContentType.JSON)
                .body(Map.of("email", "personne" + System.nanoTime() + "@example.bf"))
                .when().post("/api/auth/password/forgot")
                .then().statusCode(202);
    }

    @Test
    void reset_with_a_bogus_token_is_rejected() {
        given().contentType(ContentType.JSON)
                .body(Map.of("token", "not-a-real-token", "password", "Quelconque!2026"))
                .when().post("/api/auth/password/reset")
                .then().statusCode(422).body("code", equalTo("RESET_TOKEN_INVALID"));
    }

    @Test
    void super_admin_can_list_users() {
        var login = given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "admin@plateforme.bf", "password", "ChangeMe!2026"))
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .extract().response();

        given().header("Authorization", "Bearer " + login.path("accessToken"))
                .when().get("/api/users")
                .then().statusCode(200)
                .body("content", notNullValue());
    }

    @Test
    void guest_quick_session_needs_only_a_phone_and_reuses_the_same_account() {
        String phone = "+226 70" + (System.nanoTime() % 1_000_000);

        var first = given().contentType(ContentType.JSON)
                .body(Map.of("phone", phone))
                .when().post("/api/auth/guest-quick")
                .then().statusCode(200)
                .body("accessToken", notNullValue())
                .body("user.guest", equalTo(true))
                .extract().response();
        String userId = first.path("user.id");

        // scanning again with the same phone returns the same guest account
        var second = given().contentType(ContentType.JSON)
                .body(Map.of("phone", phone))
                .when().post("/api/auth/guest-quick")
                .then().statusCode(200)
                .extract().response();
        assertThat(second.path("user.id").toString()).isEqualTo(userId);

        // an invalid phone is rejected
        given().contentType(ContentType.JSON)
                .body(Map.of("phone", "abc"))
                .when().post("/api/auth/guest-quick")
                .then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void guest_lookup_never_creates_an_account_and_needs_existing_tickets() {
        String phone = "+226 71" + (System.nanoTime() % 1_000_000);

        // unknown phone -> 404, and nothing was created (guest-quick then makes a NEW account)
        given().contentType(ContentType.JSON).body(Map.of("phone", phone))
                .when().post("/api/auth/guest-lookup")
                .then().statusCode(404);
        given().contentType(ContentType.JSON).body(Map.of("phone", phone))
                .when().post("/api/auth/guest-lookup")
                .then().statusCode(404);

        // a guest that exists but holds no ticket is not opened either
        given().contentType(ContentType.JSON).body(Map.of("phone", phone))
                .when().post("/api/auth/guest-quick").then().statusCode(200);
        given().contentType(ContentType.JSON).body(Map.of("phone", phone))
                .when().post("/api/auth/guest-lookup")
                .then().statusCode(404);

        // invalid phone still rejected by validation
        given().contentType(ContentType.JSON).body(Map.of("phone", "abc"))
                .when().post("/api/auth/guest-lookup")
                .then().statusCode(400);
    }
}
