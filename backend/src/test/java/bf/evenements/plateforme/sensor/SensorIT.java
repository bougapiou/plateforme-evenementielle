package bf.evenements.plateforme.sensor;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SensorIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private String publishedEvent(String orga, String admin, long n) {
        String eventId = as(orga).body(Map.of("nom", "Laser " + n,
                        "dateDebut", "2027-10-25T09:00:00Z", "dateFin", "2027-11-03T20:00:00Z",
                        "ville", "Ouagadougou"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");
        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + eventId + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/publish").then().statusCode(200);
        return eventId;
    }

    @Test
    void sensor_counts_entries_and_exits_with_its_key_and_can_be_revoked() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("sn-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();
        String eventId = publishedEvent(orga, admin, n);

        var created = as(orga).body(Map.of("nom", "Porte principale"))
                .when().post("/api/events/" + eventId + "/sensors")
                .then().statusCode(201)
                .body("cle", notNullValue())
                .extract().response();
        String key = created.path("cle");
        String sensorId = created.path("capteur.id");

        // no key / wrong key -> 401
        given().contentType(ContentType.JSON)
                .when().post("/api/sensors/entry").then().statusCode(401);
        given().header("X-Sensor-Key", "pne_nope").contentType(ContentType.JSON)
                .when().post("/api/sensors/entry").then().statusCode(401);

        // 1 entry (no body), then 4 entries in a batch, then 2 exits
        given().header("X-Sensor-Key", key)
                .when().post("/api/sensors/entry")
                .then().statusCode(200).body("entrees", equalTo(1));
        given().header("X-Sensor-Key", key)
                .when().post("/api/sensors/entry?count=4")
                .then().statusCode(200).body("entrees", equalTo(5));
        given().header("X-Sensor-Key", key)
                .when().post("/api/sensors/exit?count=2")
                .then().statusCode(200)
                .body("entrees", equalTo(5)).body("sorties", equalTo(2))
                .body("presents", equalTo(3));

        // out-of-range count rejected
        given().header("X-Sensor-Key", key)
                .when().post("/api/sensors/entry?count=0")
                .then().statusCode(422).body("code", equalTo("INVALID_COUNT"));

        // merged into the attendance view, apart from ticket scans
        as(orga).when().get("/api/events/" + eventId + "/attendance")
                .then().statusCode(200)
                .body("comptagePhysique.entrees", equalTo(5))
                .body("comptagePhysique.sorties", equalTo(2))
                .body("comptagePhysique.presents", equalTo(3))
                .body("event.entrees", equalTo(0));

        // organiser sees per-sensor totals; the key itself is never listed again
        as(orga).when().get("/api/events/" + eventId + "/sensors")
                .then().statusCode(200)
                .body("[0].entrees", equalTo(5))
                .body("[0].sorties", equalTo(2))
                .body("[0].cle", equalTo(null));

        // another organiser cannot manage this event's sensors
        String other = TestAuth.organizerToken("sn-other-" + n + "@example.bf");
        as(other).when().get("/api/events/" + eventId + "/sensors").then().statusCode(403);

        // revoked key stops working
        as(orga).when().delete("/api/events/" + eventId + "/sensors/" + sensorId)
                .then().statusCode(204);
        given().header("X-Sensor-Key", key)
                .when().post("/api/sensors/entry").then().statusCode(401);
    }

    @Test
    void sensor_is_refused_while_the_event_is_not_open() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("sn2-orga-" + n + "@example.bf");
        String eventId = as(orga).body(Map.of("nom", "Brouillon " + n,
                        "dateDebut", "2027-10-25T09:00:00Z", "dateFin", "2027-11-03T20:00:00Z",
                        "ville", "Ouagadougou"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");
        String key = as(orga).body(Map.of("nom", "Test"))
                .when().post("/api/events/" + eventId + "/sensors")
                .then().statusCode(201).extract().path("cle");

        given().header("X-Sensor-Key", key)
                .when().post("/api/sensors/entry")
                .then().statusCode(422).body("code", equalTo("EVENT_NOT_ACTIVE"));
    }
}
