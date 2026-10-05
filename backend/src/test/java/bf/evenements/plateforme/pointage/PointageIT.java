package bf.evenements.plateforme.pointage;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PointageIT extends AbstractIntegrationTest {

    /** The seeder has already populated the ministries ; this test adds its own extra stand
     *  to work with fixed, known counts regardless of what else runs concurrently. */
    @Test
    void visitor_signals_several_stands_at_once_without_any_event() {
        List<Map<String, String>> stands = given().when().get("/api/public/pointage/stands")
                .then().statusCode(200).extract().jsonPath().getList("$");
        org.hamcrest.MatcherAssert.assertThat(stands.size(), greaterThanOrEqualTo(19));
        int total = stands.size();
        String standA = stands.get(0).get("id");
        String standB = stands.get(1).get("id");
        String standC = stands.get(2).get("id");

        // ticking two stands at once, with a first name only
        given().contentType(ContentType.JSON)
                .body(Map.of("standIds", List.of(standA, standB), "prenom", "Awa"))
                .when().post("/api/public/pointage/passages")
                .then().statusCode(201).body("enregistres", equalTo(2));

        // anonymous passage on a third stand
        given().contentType(ContentType.JSON).body(Map.of("standIds", List.of(standC)))
                .when().post("/api/public/pointage/passages")
                .then().statusCode(201).body("enregistres", equalTo(1));

        // no stand selected at all -> rejected
        given().contentType(ContentType.JSON).body(Map.of("standIds", List.of()))
                .when().post("/api/public/pointage/passages")
                .then().statusCode(400);

        given().when().get("/api/public/pointage/stands/" + standA + "/stats")
                .then().statusCode(200)
                .body("visites", equalTo(1))
                .body("visitesIdentifiees", equalTo(1))
                .body("visitesAnonymes", equalTo(0));

        given().when().get("/api/public/pointage/stands/" + standC + "/stats")
                .then().statusCode(200)
                .body("visites", equalTo(1))
                .body("visitesIdentifiees", equalTo(0))
                .body("visitesAnonymes", equalTo(1));

        given().when().get("/api/public/pointage/stats")
                .then().statusCode(200)
                .body("totalVisites", greaterThanOrEqualTo(3))
                .body("stands", hasSize(total));
    }

    @Test
    void unknown_stand_stats_is_a_404() {
        given().when().get("/api/public/pointage/stands/" + UUID.randomUUID() + "/stats")
                .then().statusCode(404);
    }
}
