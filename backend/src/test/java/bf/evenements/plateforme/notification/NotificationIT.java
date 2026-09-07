package bf.evenements.plateforme.notification;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private String openEvent(String orga, String admin) {
        long n = System.nanoTime();
        String id = as(orga).body(Map.of("nom", "Semaine du Numérique " + n,
                        "dateDebut", "2027-11-01T08:00:00Z", "dateFin", "2027-11-05T18:00:00Z",
                        "ville", "Ouagadougou"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");
        as(orga).when().post("/api/events/" + id + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + id + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + id + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + id + "/open-registrations").then().statusCode(200);
        return id;
    }

    @Test
    void confirmed_registration_and_broadcast_produce_in_app_notifications() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("nt-orga-" + n + "@example.bf");
        String eventId = openEvent(orga, TestAuth.adminToken());
        String user = TestAuth.registerAndToken("nt-user-" + n + "@example.bf", "PARTICULIER");

        as(user).body(Map.of("type", "PARTICULIER",
                        "participants", List.of(Map.of("nom", "Nikiema"))))
                .when().post("/api/events/" + eventId + "/registrations")
                .then().statusCode(201).body("statut", equalTo("CONFIRMEE"));

        as(user).when().get("/api/notifications")
                .then().statusCode(200)
                .body("content.type", hasItem("INSCRIPTION_CONFIRMEE"))
                .body("totalElements", greaterThanOrEqualTo(1));

        int unread = as(user).when().get("/api/notifications/unread-count")
                .then().statusCode(200).extract().path("count");
        org.junit.jupiter.api.Assertions.assertTrue(unread >= 1);

        // organiser broadcast
        as(orga).body(Map.of("titre", "Changement de salle",
                        "contenu", "La cérémonie d'ouverture se tiendra au Palais des sports."))
                .when().post("/api/events/" + eventId + "/broadcast")
                .then().statusCode(200).body("destinataires", greaterThanOrEqualTo(1));

        as(user).when().get("/api/notifications")
                .then().statusCode(200)
                .body("content.titre", hasItem("Changement de salle"));

        // mark all read
        as(user).when().post("/api/notifications/read-all").then().statusCode(200);
        int after = as(user).when().get("/api/notifications/unread-count")
                .then().statusCode(200).extract().path("count");
        org.junit.jupiter.api.Assertions.assertEquals(0, after);
    }

    @Test
    void paying_a_ticket_order_notifies_the_buyer() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("ntp-orga-" + n + "@example.bf");
        String eventId = openEvent(orga, TestAuth.adminToken());
        String ticketCat = as(orga).body(Map.of("nom", "Pass", "prixMontant", 2000,
                        "portee", "EVENEMENT", "quantiteTotale", 50))
                .when().post("/api/events/" + eventId + "/tickets").then().statusCode(201)
                .extract().path("id");

        String buyer = TestAuth.registerAndToken("ntp-buyer-" + n + "@example.bf", "PARTICULIER");
        String orderId = as(buyer).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", ticketCat, "quantite", 1))))
                .when().post("/api/ticket-orders").then().statusCode(201).extract().path("id");
        as(buyer).when().post("/api/ticket-orders/" + orderId + "/pay-sandbox").then().statusCode(200);

        as(buyer).when().get("/api/notifications")
                .then().statusCode(200)
                .body("content.type", hasItem("BILLET_DISPONIBLE"));
    }
}
