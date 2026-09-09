package bf.evenements.plateforme.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthFlowIT extends AbstractIntegrationTest {

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
                        "lastName", "Ouedraogo"))
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
                "firstName", "Paul", "lastName", "Kabore");

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
                .body(Map.of("email", email, "firstName", "Awa", "lastName", "Invitee"))
                .when().post("/api/auth/guest")
                .then().statusCode(200)
                .body("user.guest", equalTo(true))
                .body("user.roles", hasItem("PARTICIPANT"))
                .extract().path("accessToken");

        // a guest cannot log in (no password chosen)
        given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "whatever"))
                .when().post("/api/auth/login").then().statusCode(401);

        // --- claim the account ---
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
                .body(Map.of("email", email, "firstName", "X", "lastName", "Y"))
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
                .body(Map.of("email", email, "firstName", "B", "lastName", "C"))
                .when().post("/api/auth/guest").then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "Passw0rd!2026",
                        "firstName", "B", "lastName", "C"))
                .when().post("/api/auth/register")
                .then().statusCode(201)
                .body("user.guest", equalTo(false));
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
}
