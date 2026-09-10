package bf.evenements.plateforme.checkin;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.QrTestUtil;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Entry + exit scanning and the resulting flow count. */
class ExitControlIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private String buyTicketToken(String orga, String admin, String eventId) throws Exception {
        String cat = as(orga).body(Map.of("nom", "Entrée", "prixMontant", 1000,
                        "portee", "EVENEMENT", "quantiteTotale", 100))
                .when().post("/api/events/" + eventId + "/tickets").then().statusCode(201)
                .extract().path("id");
        String buyer = TestAuth.registerAndToken(
                "ex-buyer-" + System.nanoTime() + "@example.bf", "PARTICULIER");
        String orderId = as(buyer).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", cat, "quantite", 1))))
                .when().post("/api/ticket-orders").then().statusCode(201).extract().path("id");
        as(buyer).when().post("/api/ticket-orders/" + orderId + "/pay-sandbox").then().statusCode(200);
        String ticketId = as(buyer).when().get("/api/tickets/my")
                .then().statusCode(200).extract().path("[0].id");
        byte[] png = given().header("Authorization", "Bearer " + buyer)
                .when().get("/api/tickets/" + ticketId + "/qr.png")
                .then().statusCode(200).extract().asByteArray();
        return QrTestUtil.decode(png);
    }

    @Test
    void entry_exit_reentry_are_counted() throws Exception {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("ex-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();

        String eventId = as(orga).body(Map.of("nom", "SIAO " + n,
                        "dateDebut", "2027-10-25T09:00:00Z", "dateFin", "2027-11-03T20:00:00Z",
                        "ville", "Ouagadougou", "controleSortie", true))
                .when().post("/api/events").then().statusCode(201)
                .body("controleSortie", equalTo(true))
                .extract().path("id");
        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + eventId + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/open-registrations").then().statusCode(200);

        String token = buyTicketToken(orga, admin, eventId);

        // ENTREE -> valid
        as(orga).body(Map.of("token", token, "eventId", eventId, "sens", "ENTREE"))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("VALIDE")).body("sens", equalTo("ENTREE"))
                .body("reentree", equalTo(false));

        // ENTREE again -> already inside
        as(orga).body(Map.of("token", token, "eventId", eventId, "sens", "ENTREE"))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("DEJA_UTILISE"));

        // SORTIE -> valid
        as(orga).body(Map.of("token", token, "eventId", eventId, "sens", "SORTIE"))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("VALIDE")).body("sens", equalTo("SORTIE"));

        // SORTIE again -> not inside
        as(orga).body(Map.of("token", token, "eventId", eventId, "sens", "SORTIE"))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("DEJA_UTILISE"));

        // ENTREE -> re-entry
        as(orga).body(Map.of("token", token, "eventId", eventId, "sens", "ENTREE"))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("VALIDE")).body("reentree", equalTo(true));

        // flow count
        as(orga).when().get("/api/events/" + eventId + "/checkin-stats")
                .then().statusCode(200)
                .body("entrees", equalTo(2))
                .body("sorties", equalTo(1))
                .body("presents", equalTo(1))
                .body("reentrees", equalTo(1));
    }

    @Test
    void without_exit_control_a_ticket_is_single_use() throws Exception {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("ex2-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();
        String eventId = as(orga).body(Map.of("nom", "Foire " + n,
                        "dateDebut", "2027-09-01T09:00:00Z", "dateFin", "2027-09-05T18:00:00Z",
                        "ville", "Bobo"))
                .when().post("/api/events").then().statusCode(201)
                .body("controleSortie", equalTo(false))
                .extract().path("id");
        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + eventId + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/open-registrations").then().statusCode(200);

        String token = buyTicketToken(orga, admin, eventId);
        as(orga).body(Map.of("token", token, "eventId", eventId))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("VALIDE"));
        as(orga).body(Map.of("token", token, "eventId", eventId))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("DEJA_UTILISE"));
    }
}
