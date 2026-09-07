package bf.evenements.plateforme.support;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import java.util.Map;

/** Helpers to obtain access tokens in integration tests. */
public final class TestAuth {

    private TestAuth() {
    }

    public static final String ADMIN_EMAIL = "admin@plateforme.bf";
    public static final String ADMIN_PASSWORD = "ChangeMe!2026";

    public static String registerAndToken(String email, String type) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "Secret123",
                        "firstName", "Test", "lastName", "User", "type", type))
                .when().post("/api/auth/register")
                .then().statusCode(201)
                .extract().path("accessToken");
    }

    public static String login(String email, String password) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", password))
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");
    }

    public static String adminToken() {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    /**
     * Registers a user, applies for an organiser profile, has the admin approve it
     * and returns a fresh access token carrying the organiser permissions.
     */
    public static String organizerToken(String email) {
        String userToken = registerAndToken(email, "PARTICULIER");
        String organizerId = given().header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(Map.of("nomAffichage", "Organisateur " + email))
                .when().post("/api/organizers/apply")
                .then().statusCode(201)
                .extract().path("id");
        given().header("Authorization", "Bearer " + adminToken())
                .when().post("/api/organizers/" + organizerId + "/approve")
                .then().statusCode(200);
        return login(email, "Secret123");
    }
}
