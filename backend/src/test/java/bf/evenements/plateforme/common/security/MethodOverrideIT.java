package bf.evenements.plateforme.common.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A network appliance in front of production blocks PUT/PATCH/DELETE outright
 * (confirmed: an identical GET reaches the app and gets 401, a PUT never
 * reaches it at all). {@link MethodOverrideFilter} lets a POST carrying
 * {@code X-HTTP-Method-Override} stand in for the real verb.
 */
class MethodOverrideIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    @Test
    void post_with_override_header_updates_the_event_like_a_real_put() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("override-" + n + "@example.bf");
        String categoryId = given().when().get("/api/event-categories")
                .then().statusCode(200).extract().path("[0].id");

        Map<String, Object> create = new HashMap<>();
        create.put("nom", "Avant");
        create.put("categoryId", categoryId);
        create.put("dateDebut", "2027-11-01T08:00:00Z");
        create.put("dateFin", "2027-11-05T18:00:00Z");
        create.put("ville", "Ouagadougou");
        create.put("hasActivities", false);
        create.put("standsActifs", false);
        String eventId = as(orga).body(create).when().post("/api/events")
                .then().statusCode(201).extract().path("id");

        Map<String, Object> update = new HashMap<>(create);
        update.put("nom", "Après (via override)");

        // A real PUT works as before...
        as(orga).body(update).when().put("/api/events/" + eventId)
                .then().statusCode(200)
                .body("nom", equalTo("Après (via override)"));

        // ...and so does a POST carrying the override header, to the exact
        // same URL — this is the path a client behind the blocking gateway
        // must use instead of a real PUT.
        update.put("nom", "Après (encore, via override)");
        as(orga).header("X-HTTP-Method-Override", "PUT")
                .body(update).when().post("/api/events/" + eventId)
                .then().statusCode(200)
                .body("nom", equalTo("Après (encore, via override)"));

        // A plain POST to the same URL, without the header, must NOT be
        // silently treated as an update — /api/events/{id} has no @PostMapping.
        as(orga).body(update).when().post("/api/events/" + eventId)
                .then().statusCode(405);
    }

    @Test
    void override_header_is_ignored_for_a_disallowed_value() {
        // Only PUT/PATCH/DELETE are recognised; anything else must not let a
        // POST masquerade as some other verb.
        as(TestAuth.adminToken())
                .header("X-HTTP-Method-Override", "TRACE")
                .when().post("/api/events/00000000-0000-0000-0000-000000000000")
                .then().statusCode(405);
    }

    @Test
    void delete_via_override_removes_a_draft_event() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("override-del-" + n + "@example.bf");
        String categoryId = given().when().get("/api/event-categories")
                .then().statusCode(200).extract().path("[0].id");

        Map<String, Object> create = new HashMap<>();
        create.put("nom", "À supprimer");
        create.put("categoryId", categoryId);
        create.put("dateDebut", "2027-11-01T08:00:00Z");
        create.put("dateFin", "2027-11-05T18:00:00Z");
        create.put("ville", "Ouagadougou");
        create.put("hasActivities", false);
        create.put("standsActifs", false);
        String eventId = as(orga).body(create).when().post("/api/events")
                .then().statusCode(201).extract().path("id");

        as(orga).header("X-HTTP-Method-Override", "DELETE")
                .when().post("/api/events/" + eventId)
                .then().statusCode(204);

        as(orga).when().get("/api/events/" + eventId)
                .then().statusCode(404);
    }
}
