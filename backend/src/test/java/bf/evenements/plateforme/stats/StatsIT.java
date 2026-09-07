package bf.evenements.plateforme.stats;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatsIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    @Test
    void organizer_and_event_and_admin_statistics() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("st-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();

        String eventId = as(orga).body(Map.of("nom", "Salon de l'emploi " + n,
                        "dateDebut", "2027-06-10T08:00:00Z", "dateFin", "2027-06-12T18:00:00Z",
                        "ville", "Ouagadougou"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");
        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + eventId + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/open-registrations").then().statusCode(200);
        String cat = as(orga).body(Map.of("nom", "Standard", "prixMontant", 5000,
                        "portee", "EVENEMENT", "quantiteTotale", 10))
                .when().post("/api/events/" + eventId + "/tickets").then().statusCode(201)
                .extract().path("id");

        String buyer = TestAuth.registerAndToken("st-buyer-" + n + "@example.bf", "PARTICULIER");
        String orderId = as(buyer).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", cat, "quantite", 3))))
                .when().post("/api/ticket-orders").then().statusCode(201).extract().path("id");
        as(buyer).when().post("/api/ticket-orders/" + orderId + "/pay-sandbox").then().statusCode(200);

        as(orga).when().get("/api/stats/organizer/overview")
                .then().statusCode(200)
                .body("billetsVendus", greaterThanOrEqualTo(3))
                .body("revenus", greaterThanOrEqualTo(15000f));

        as(orga).when().get("/api/stats/events/" + eventId)
                .then().statusCode(200)
                .body("billetsTotal", equalTo(10))
                .body("billetsVendus", equalTo(3))
                .body("billetsRestants", equalTo(7))
                .body("tauxRemplissage", equalTo(30.0f))
                .body("revenus", equalTo(15000f))
                .body("paiementsReussis", greaterThanOrEqualTo(1));

        as(orga).when().get("/api/stats/events/" + eventId + "/series")
                .then().statusCode(200)
                .body("quotidien", notNullValue())
                .body("billetsParCategorie", hasSize(1))
                .body("billetsParCategorie[0].valeur", equalTo(3));

        as(admin).when().get("/api/stats/admin/overview")
                .then().statusCode(200)
                .body("evenementsTotal", greaterThanOrEqualTo(1))
                .body("billetsVendus", greaterThanOrEqualTo(3));

        // an organiser cannot read global stats
        as(orga).when().get("/api/stats/admin/overview").then().statusCode(403);
    }
}
