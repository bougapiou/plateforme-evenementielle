package bf.evenements.plateforme.invoice;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InvoiceIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    @Test
    void a_paid_ticket_order_generates_an_invoice_and_a_receipt() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("inv-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();

        String eventId = as(orga).body(Map.of("nom", "Forum " + n,
                        "dateDebut", "2027-05-01T08:00:00Z", "dateFin", "2027-05-02T18:00:00Z",
                        "ville", "Ouagadougou"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");
        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + eventId + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/open-registrations").then().statusCode(200);
        String ticketCat = as(orga).body(Map.of("nom", "Standard", "prixMontant", 10000,
                        "portee", "EVENEMENT", "quantiteTotale", 50))
                .when().post("/api/events/" + eventId + "/tickets").then().statusCode(201)
                .extract().path("id");

        String buyer = TestAuth.registerAndToken("inv-buyer-" + n + "@example.bf", "PARTICULIER");
        String orderId = as(buyer).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", ticketCat, "quantite", 2))))
                .when().post("/api/ticket-orders").then().statusCode(201).extract().path("id");

        String paymentId = as(buyer).body(Map.of("targetType", "TICKET_ORDER", "targetId", orderId))
                .when().post("/api/payments").then().statusCode(201).extract().path("id");
        String reference = as(buyer).when().get("/api/payments/" + paymentId)
                .then().statusCode(200).extract().path("reference");
        as(buyer).when().post("/api/payments/" + reference + "/simulate?outcome=SUCCESS")
                .then().statusCode(200).body("statut", equalTo("REUSSI"));

        as(buyer).when().get("/api/invoices/my")
                .then().statusCode(200)
                .body("$", hasSize(2))
                .body("type", hasItems("FACTURE", "RECU"))
                .body("montant", hasItems(20000.0f));

        var invoices = as(buyer).when().get("/api/payments/" + paymentId + "/invoices")
                .then().statusCode(200).body("$", hasSize(2)).extract().response();
        String invoiceId = invoices.path("[0].id");

        byte[] pdf = given().header("Authorization", "Bearer " + buyer)
                .when().get("/api/invoices/" + invoiceId + "/pdf")
                .then().statusCode(200).contentType("application/pdf").extract().asByteArray();
        org.junit.jupiter.api.Assertions.assertEquals("%PDF",
                new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));

        // a stranger cannot download it
        String stranger = TestAuth.registerAndToken("inv-x-" + n + "@example.bf", "PARTICULIER");
        given().header("Authorization", "Bearer " + stranger)
                .when().get("/api/invoices/" + invoiceId + "/pdf").then().statusCode(403);
    }

    @Test
    void confirmed_registration_yields_a_confirmation_pdf() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("conf-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();
        String eventId = as(orga).body(Map.of("nom", "Atelier " + n,
                        "dateDebut", "2027-04-01T08:00:00Z", "dateFin", "2027-04-01T18:00:00Z",
                        "ville", "Bobo-Dioulasso"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");
        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + eventId + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/open-registrations").then().statusCode(200);

        String user = TestAuth.registerAndToken("conf-user-" + n + "@example.bf", "PARTICULIER");
        String regId = as(user).body(Map.of("type", "PARTICULIER",
                        "participants", List.of(Map.of("nom", "Sana", "prenom", "Ibrahim"))))
                .when().post("/api/events/" + eventId + "/registrations")
                .then().statusCode(201).body("statut", equalTo("CONFIRMEE"))
                .extract().path("id");

        byte[] pdf = given().header("Authorization", "Bearer " + user)
                .when().get("/api/registrations/" + regId + "/confirmation.pdf")
                .then().statusCode(200).contentType("application/pdf").extract().asByteArray();
        org.junit.jupiter.api.Assertions.assertEquals("%PDF",
                new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
    }
}
