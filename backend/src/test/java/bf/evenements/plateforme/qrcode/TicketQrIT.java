package bf.evenements.plateforme.qrcode;

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

class TicketQrIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    @Test
    void paid_ticket_exposes_a_qr_image_and_a_pdf_with_access_control() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("qr-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();

        String eventId = as(orga).body(Map.of("nom", "FESPACO " + n,
                        "dateDebut", "2027-02-25T09:00:00Z", "dateFin", "2027-03-04T20:00:00Z",
                        "ville", "Ouagadougou", "lieu", "Ciné Burkina"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");
        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + eventId + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/open-registrations").then().statusCode(200);

        String ticketCatId = as(orga).body(Map.of("nom", "Pass festival", "prixMontant", 5000,
                        "portee", "EVENEMENT", "quantiteTotale", 100))
                .when().post("/api/events/" + eventId + "/tickets")
                .then().statusCode(201).extract().path("id");

        String buyer = TestAuth.registerAndToken("qr-buyer-" + n + "@example.bf", "PARTICULIER");
        String orderId = as(buyer).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", ticketCatId, "quantite", 1))))
                .when().post("/api/ticket-orders").then().statusCode(201).extract().path("id");
        as(buyer).when().post("/api/ticket-orders/" + orderId + "/pay-sandbox")
                .then().statusCode(200).body("statut", equalTo("PAYEE"));

        var ticket = as(buyer).when().get("/api/tickets/my")
                .then().statusCode(200).body("$", hasSize(1))
                .body("[0].qrImageUrl", notNullValue())
                .body("[0].pdfUrl", notNullValue())
                .extract().response();
        String ticketId = ticket.path("[0].id");

        // QR image
        byte[] png = given().header("Authorization", "Bearer " + buyer)
                .when().get("/api/tickets/" + ticketId + "/qr.png")
                .then().statusCode(200).contentType("image/png")
                .extract().asByteArray();
        org.junit.jupiter.api.Assertions.assertTrue(png.length > 100);

        // PDF
        byte[] pdf = given().header("Authorization", "Bearer " + buyer)
                .when().get("/api/tickets/" + ticketId + "/pdf")
                .then().statusCode(200).contentType("application/pdf")
                .extract().asByteArray();
        org.junit.jupiter.api.Assertions.assertEquals("%PDF",
                new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));

        // access control: a stranger is denied, the organiser is allowed
        String stranger = TestAuth.registerAndToken("qr-stranger-" + n + "@example.bf", "PARTICULIER");
        given().header("Authorization", "Bearer " + stranger)
                .when().get("/api/tickets/" + ticketId + "/qr.png").then().statusCode(403);
        given().header("Authorization", "Bearer " + orga)
                .when().get("/api/tickets/" + ticketId + "/qr.png").then().statusCode(200);
    }
}
