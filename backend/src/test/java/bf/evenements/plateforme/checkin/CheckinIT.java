package bf.evenements.plateforme.checkin;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class CheckinIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private String decodeQr(byte[] png) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(img)));
        return new MultiFormatReader().decode(bitmap).getText();
    }

    @Test
    void scan_flow_valid_then_already_used_then_invalid_and_staff_can_scan() throws Exception {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("ck-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();

        String eventId = as(orga).body(Map.of("nom", "SIAO " + n,
                        "dateDebut", "2027-10-25T09:00:00Z", "dateFin", "2027-11-03T20:00:00Z",
                        "ville", "Ouagadougou"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");
        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + eventId + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/open-registrations").then().statusCode(200);

        String ticketCat = as(orga).body(Map.of("nom", "Entrée", "prixMontant", 1000,
                        "portee", "EVENEMENT", "quantiteTotale", 100))
                .when().post("/api/events/" + eventId + "/tickets").then().statusCode(201)
                .extract().path("id");

        String buyer = TestAuth.registerAndToken("ck-buyer-" + n + "@example.bf", "PARTICULIER");
        String orderId = as(buyer).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", ticketCat, "quantite", 1))))
                .when().post("/api/ticket-orders").then().statusCode(201).extract().path("id");
        as(buyer).when().post("/api/ticket-orders/" + orderId + "/pay-sandbox").then().statusCode(200);
        String ticketId = as(buyer).when().get("/api/tickets/my")
                .then().statusCode(200).extract().path("[0].id");

        byte[] png = given().header("Authorization", "Bearer " + buyer)
                .when().get("/api/tickets/" + ticketId + "/qr.png")
                .then().statusCode(200).extract().asByteArray();
        String qrToken = decodeQr(png);

        // 1) valid entry (organiser scans their own event)
        as(orga).body(Map.of("token", qrToken, "eventId", eventId))
                .when().post("/api/checkins/scan")
                .then().statusCode(200)
                .body("resultat", equalTo("VALIDE"))
                .body("participantNom", notNullValue())
                .body("numeroBillet", notNullValue())
                .body("heureEntree", notNullValue());

        // 2) second scan -> already used
        as(orga).body(Map.of("token", qrToken, "eventId", eventId))
                .when().post("/api/checkins/scan")
                .then().statusCode(200)
                .body("resultat", equalTo("DEJA_UTILISE"))
                .body("premierControleLe", notNullValue());

        // 3) garbage token -> invalid
        as(orga).body(Map.of("token", "QRnope", "eventId", eventId))
                .when().post("/api/checkins/scan")
                .then().statusCode(200).body("resultat", equalTo("INVALIDE"));

        // a participant has no scan permission
        as(buyer).body(Map.of("token", qrToken, "eventId", eventId))
                .when().post("/api/checkins/scan").then().statusCode(403);

        // stats
        as(orga).when().get("/api/events/" + eventId + "/checkin-stats")
                .then().statusCode(200)
                .body("valides", greaterThanOrEqualTo(1))
                .body("dejaUtilises", greaterThanOrEqualTo(1))
                .body("invalides", greaterThanOrEqualTo(1));

        // add a staff member -> they can scan after re-login
        String staffEmail = "ck-staff-" + n + "@example.bf";
        TestAuth.registerAndToken(staffEmail, "PARTICULIER");
        as(orga).body(Map.of("email", staffEmail))
                .when().post("/api/events/" + eventId + "/staff").then().statusCode(201);
        String staffToken = TestAuth.login(staffEmail, "Secret123");

        // buy a second ticket to scan with the staff account
        String buyer2 = TestAuth.registerAndToken("ck-buyer2-" + n + "@example.bf", "PARTICULIER");
        String order2 = as(buyer2).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", ticketCat, "quantite", 1))))
                .when().post("/api/ticket-orders").then().statusCode(201).extract().path("id");
        as(buyer2).when().post("/api/ticket-orders/" + order2 + "/pay-sandbox").then().statusCode(200);
        String ticket2 = as(buyer2).when().get("/api/tickets/my").then().statusCode(200)
                .extract().path("[0].id");
        byte[] png2 = given().header("Authorization", "Bearer " + buyer2)
                .when().get("/api/tickets/" + ticket2 + "/qr.png").then().statusCode(200)
                .extract().asByteArray();

        as(staffToken).body(Map.of("token", decodeQr(png2), "eventId", eventId))
                .when().post("/api/checkins/scan")
                .then().statusCode(200).body("resultat", equalTo("VALIDE"));
    }
}
