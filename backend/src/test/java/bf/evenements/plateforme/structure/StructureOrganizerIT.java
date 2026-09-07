package bf.evenements.plateforme.structure;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StructureOrganizerIT extends AbstractIntegrationTest {

    @Test
    void structure_lifecycle_members_and_admin_verification() {
        long n = System.nanoTime();
        String ownerToken = TestAuth.registerAndToken("owner" + n + "@example.bf", "STRUCTURE");

        String structureId = given().header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "raisonSociale", "Entreprise " + n,
                        "typeStructure", "ENTREPRISE",
                        "rccm", "RCCM-" + n,
                        "ville", "Ouagadougou"))
                .when().post("/api/structures")
                .then().statusCode(201)
                .body("statut", equalTo("EN_ATTENTE"))
                .body("myRole", equalTo("PROPRIETAIRE"))
                .extract().path("id");

        given().header("Authorization", "Bearer " + ownerToken)
                .when().get("/api/structures/mine")
                .then().statusCode(200)
                .body("id", hasItem(structureId));

        // add a member
        String memberEmail = "member" + n + "@example.bf";
        TestAuth.registerAndToken(memberEmail, "PARTICULIER");
        given().header("Authorization", "Bearer " + ownerToken)
                .contentType(ContentType.JSON)
                .body(Map.of("email", memberEmail, "roleInterne", "MEMBRE", "fonction", "Commercial"))
                .when().post("/api/structures/" + structureId + "/members")
                .then().statusCode(201)
                .body("email", equalTo(memberEmail));

        given().header("Authorization", "Bearer " + ownerToken)
                .when().get("/api/structures/" + structureId + "/members")
                .then().statusCode(200)
                .body("roleInterne", hasItem("PROPRIETAIRE"))
                .body("$", hasSize(2));

        // an unrelated user cannot read the structure
        String strangerToken = TestAuth.registerAndToken("stranger" + n + "@example.bf", "PARTICULIER");
        given().header("Authorization", "Bearer " + strangerToken)
                .when().get("/api/structures/" + structureId)
                .then().statusCode(403);

        // admin verifies the structure
        String adminToken = TestAuth.adminToken();
        given().header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(Map.of("statut", "VERIFIEE"))
                .when().patch("/api/structures/" + structureId + "/status")
                .then().statusCode(200)
                .body("statut", equalTo("VERIFIEE"));
    }

    @Test
    void organizer_application_and_approval_grants_event_permissions() {
        long n = System.nanoTime();
        String email = "orga" + n + "@example.bf";
        String userToken = TestAuth.registerAndToken(email, "PARTICULIER");

        String organizerId = given().header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(Map.of("nomAffichage", "Comité " + n))
                .when().post("/api/organizers/apply")
                .then().statusCode(201)
                .body("statut", equalTo("EN_ATTENTE"))
                .extract().path("id");

        // duplicate application rejected
        given().header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(Map.of("nomAffichage", "Comité bis"))
                .when().post("/api/organizers/apply")
                .then().statusCode(409);

        String adminToken = TestAuth.adminToken();
        given().header("Authorization", "Bearer " + adminToken)
                .when().post("/api/organizers/" + organizerId + "/approve")
                .then().statusCode(200)
                .body("statut", equalTo("ACTIF"));

        // after re-login the user carries organiser permissions
        String refreshedToken = TestAuth.login(email, "Secret123");
        given().header("Authorization", "Bearer " + refreshedToken)
                .when().get("/api/users/me")
                .then().statusCode(200)
                .body("roles", hasItem("ORGANISATEUR"))
                .body("permissions", hasItem("EVENT_CREATE"));
    }
}
