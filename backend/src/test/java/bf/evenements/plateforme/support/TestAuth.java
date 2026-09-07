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
}
