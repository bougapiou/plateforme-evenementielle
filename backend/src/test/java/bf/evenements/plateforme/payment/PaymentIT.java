package bf.evenements.plateforme.payment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PaymentIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private String openTicket(String orga, String admin) {
        long n = System.nanoTime();
        var res = as(orga).body(Map.of("nom", "Conf " + n,
                        "dateDebut", "2027-08-01T08:00:00Z", "dateFin", "2027-08-02T18:00:00Z",
                        "ville", "Ouagadougou"))
                .when().post("/api/events").then().statusCode(201).extract().response();
        String id = res.path("id");
        as(orga).when().post("/api/events/" + id + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + id + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + id + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + id + "/open-registrations").then().statusCode(200);
        return as(orga).body(Map.of("nom", "Pass", "prixMontant", 7500, "portee", "EVENEMENT",
                        "quantiteTotale", 20))
                .when().post("/api/events/" + id + "/tickets")
                .then().statusCode(201).extract().path("id")
                + "|" + id;
    }

    @Test
    void initiate_then_webhook_success_confirms_the_order_and_is_idempotent() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("p-orga-" + n + "@example.bf");
        String parts = openTicket(orga, TestAuth.adminToken());
        String ticketId = parts.split("\\|")[0];
        String eventId = parts.split("\\|")[1];

        String buyer = TestAuth.registerAndToken("p-buyer-" + n + "@example.bf", "PARTICULIER");
        String orderId = as(buyer).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", ticketId, "quantite", 2))))
                .when().post("/api/ticket-orders").then().statusCode(201).extract().path("id");

        var payment = as(buyer).body(Map.of("targetType", "TICKET_ORDER", "targetId", orderId,
                        "moyen", "MOBILE_MONEY_ORANGE"))
                .when().post("/api/payments")
                .then().statusCode(201)
                .body("statut", equalTo("EN_ATTENTE"))
                .body("paymentUrl", notNullValue())
                .body("montant", equalTo(15000.0f))
                .extract().response();
        String reference = payment.path("reference");

        as(buyer).when().post("/api/payments/" + reference + "/simulate?outcome=SUCCESS")
                .then().statusCode(200).body("statut", equalTo("REUSSI"));

        as(buyer).when().get("/api/ticket-orders/" + orderId)
                .then().statusCode(200).body("statut", equalTo("PAYEE"));
        as(buyer).when().get("/api/tickets/my").then().statusCode(200).body("$", hasSize(2));

        // replay is a no-op — no extra tickets
        as(buyer).when().post("/api/payments/" + reference + "/simulate?outcome=SUCCESS")
                .then().statusCode(200).body("statut", equalTo("REUSSI"));
        as(buyer).when().get("/api/tickets/my").then().statusCode(200).body("$", hasSize(2));
    }

    @Test
    void webhook_with_bad_signature_is_rejected() {
        given().contentType(ContentType.JSON)
                .header("X-Payment-Signature", "deadbeef")
                .body(Map.of("reference", "PAY-UNKNOWN", "outcome", "SUCCESS"))
                .when().post("/api/payments/webhook")
                .then().statusCode(400);
    }

    @Test
    void failed_payment_leaves_the_order_pending() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("pf-orga-" + n + "@example.bf");
        String parts = openTicket(orga, TestAuth.adminToken());
        String ticketId = parts.split("\\|")[0];
        String eventId = parts.split("\\|")[1];

        String buyer = TestAuth.registerAndToken("pf-buyer-" + n + "@example.bf", "PARTICULIER");
        String orderId = as(buyer).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", ticketId, "quantite", 1))))
                .when().post("/api/ticket-orders").then().statusCode(201).extract().path("id");

        String reference = as(buyer).body(Map.of("targetType", "TICKET_ORDER", "targetId", orderId))
                .when().post("/api/payments").then().statusCode(201).extract().path("reference");

        as(buyer).when().post("/api/payments/" + reference + "/simulate?outcome=FAILED")
                .then().statusCode(200).body("statut", equalTo("ECHOUE"));

        as(buyer).when().get("/api/ticket-orders/" + orderId)
                .then().statusCode(200).body("statut", equalTo("EN_ATTENTE"));
    }
}
