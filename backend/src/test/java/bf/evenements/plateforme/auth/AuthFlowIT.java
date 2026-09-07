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
