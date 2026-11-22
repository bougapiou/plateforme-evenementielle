package bf.evenements.plateforme.event;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.QrTestUtil;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Suppression totale d'un événement par l'admin, et liste des utilisateurs limitée aux vrais comptes. */
class EventPurgeIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    @Test
    void admin_can_fully_delete_an_event_that_has_tickets_registrations_and_checkins() throws Exception {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("purge-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();

        String eventId = as(orga).body(Map.of("nom", "Purge " + n, "dateDebut", "2027-09-01T08:00:00Z",
                        "dateFin", "2027-09-03T18:00:00Z", "ville", "Ouagadougou"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");
        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + eventId + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/open-registrations").then().statusCode(200);
        String free = as(orga).body(Map.of("nom", "Entrée", "prixMontant", 0, "portee", "EVENEMENT", "quantiteTotale", 50))
                .when().post("/api/events/" + eventId + "/tickets").then().statusCode(201).extract().path("id");

        // un participant : billet, inscription, entrée scannée
        String buyer = TestAuth.registerAndToken("purge-buyer-" + n + "@example.bf", "PARTICULIER");
        as(buyer).body(Map.of("eventId", eventId, "lignes", List.of(Map.of("eventTicketId", free, "quantite", 1))))
                .when().post("/api/ticket-orders").then().statusCode(201);
        as(buyer).body(Map.of("type", "PARTICULIER", "participants", List.of(Map.of("nom", "Test"))))
                .when().post("/api/events/" + eventId + "/registrations").then().statusCode(201);
        String ticketId = as(buyer).when().get("/api/tickets/my").then().extract()
                .path("find { it.eventId == '" + eventId + "' }.id");
        byte[] png = as(buyer).when().get("/api/tickets/" + ticketId + "/qr.png").then().statusCode(200)
                .extract().asByteArray();
        as(orga).body(Map.of("token", QrTestUtil.decode(png), "eventId", eventId))
                .when().post("/api/checkins/scan").then().statusCode(200).body("resultat", equalTo("VALIDE"));

        // l'organisateur ne peut pas ; l'admin sans « force » non plus, mais voit le bilan
        as(orga).when().delete("/api/events/" + eventId).then().statusCode(422);
        as(admin).when().delete("/api/events/" + eventId).then().statusCode(422)
                .body("code", equalTo("EVENT_HAS_DEPENDENT_DATA"));
        as(admin).when().get("/api/events/" + eventId + "/bilan-suppression").then().statusCode(200)
                .body("billets", equalTo(1)).body("inscriptions", equalTo(1)).body("entrees", equalTo(1));
        // un organisateur n'a ni le bilan ni la suppression forcée
        as(orga).when().get("/api/events/" + eventId + "/bilan-suppression").then().statusCode(403);
        as(orga).when().delete("/api/events/" + eventId + "?force=true").then().statusCode(422);

        // suppression totale par l'admin
        as(admin).when().delete("/api/events/" + eventId + "?force=true").then().statusCode(204);
        as(admin).when().get("/api/events/" + eventId).then().statusCode(404);
        as(buyer).when().get("/api/tickets/my").then().statusCode(200)
                .body("eventId", not(hasItem(eventId)));
        as(buyer).when().get("/api/tickets/" + ticketId).then().statusCode(404);
        // la personne concernée a été prévenue
        as(buyer).when().get("/api/notifications").then().statusCode(200)
                .body("content.type", hasItem("EVENEMENT_ANNULE"));
    }

    @Test
    void the_user_list_shows_real_accounts_only_unless_guests_are_requested() {
        long n = System.nanoTime();
        String admin = TestAuth.adminToken();
        String phone = "+226 73" + (n % 1_000_000);
        String guestId = given().contentType(ContentType.JSON).body(Map.of("phone", phone))
                .when().post("/api/auth/guest-quick").then().statusCode(200).extract().path("user.id");
        String realId = as(admin).body(Map.of("email", "real-" + n + "@example.bf", "firstName", "A", "lastName", "B"))
                .when().post("/api/users").then().statusCode(201).extract().path("user.id");

        as(admin).when().get("/api/users?size=500").then().statusCode(200)
                .body("content.id", hasItem(realId))
                .body("content.id", not(hasItem(guestId)));
        as(admin).when().get("/api/users?size=500&invites=true").then().statusCode(200)
                .body("content.id", hasItem(guestId));
    }
}
