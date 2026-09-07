package bf.evenements.plateforme.registration;

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

class RegistrationIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private String[] publishedEvent(String orga, String admin, boolean validation) {
        long n = System.nanoTime();
        var body = new java.util.HashMap<String, Object>(Map.of(
                "nom", "Séminaire " + n,
                "dateDebut", "2027-12-01T08:00:00Z",
                "dateFin", "2027-12-02T18:00:00Z",
                "ville", "Ouagadougou"));
        body.put("validationInscription", validation);
        var res = as(orga).body(body).when().post("/api/events")
                .then().statusCode(201).extract().response();
        String id = res.path("id");
        as(orga).when().post("/api/events/" + id + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + id + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + id + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + id + "/open-registrations").then().statusCode(200);
        return new String[] {id, res.path("slug")};
    }

    @Test
    void free_registration_is_confirmed_immediately_and_duplicates_are_blocked() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("r-orga-" + n + "@example.bf");
        String eventId = publishedEvent(orga, TestAuth.adminToken(), false)[0];
        String user = TestAuth.registerAndToken("r-user-" + n + "@example.bf", "PARTICULIER");

        as(user).body(Map.of("type", "PARTICULIER",
                        "participants", List.of(Map.of("nom", "Zongo", "prenom", "Ali"))))
                .when().post("/api/events/" + eventId + "/registrations")
                .then().statusCode(201)
                .body("statut", equalTo("CONFIRMEE"))
                .body("reference", notNullValue())
                .body("participants", hasSize(1));

        as(user).body(Map.of("type", "PARTICULIER"))
                .when().post("/api/events/" + eventId + "/registrations")
                .then().statusCode(409).body("code", equalTo("ALREADY_REGISTERED"));
    }

    @Test
    void registration_with_tickets_is_confirmed_when_the_order_is_paid() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("rt-orga-" + n + "@example.bf");
        String eventId = publishedEvent(orga, TestAuth.adminToken(), false)[0];

        String ticketId = as(orga).body(Map.of("nom", "Pass", "prixMontant", 3000,
                        "portee", "EVENEMENT", "quantiteTotale", 10))
                .when().post("/api/events/" + eventId + "/tickets")
                .then().statusCode(201).extract().path("id");

        String user = TestAuth.registerAndToken("rt-user-" + n + "@example.bf", "PARTICULIER");
        var res = as(user).body(Map.of("type", "PARTICULIER",
                        "participants", List.of(Map.of("nom", "Ouedraogo")),
                        "tickets", List.of(Map.of("eventTicketId", ticketId, "quantite", 1))))
                .when().post("/api/events/" + eventId + "/registrations")
                .then().statusCode(201)
                .body("statut", equalTo("EN_ATTENTE"))
                .body("ticketOrderId", notNullValue())
                .extract().response();
        String regId = res.path("id");
        String orderId = res.path("ticketOrderId");

        as(user).when().post("/api/ticket-orders/" + orderId + "/pay-sandbox")
                .then().statusCode(200).body("statut", equalTo("PAYEE"));

        as(user).when().get("/api/registrations/" + regId)
                .then().statusCode(200).body("statut", equalTo("CONFIRMEE"));
    }

    @Test
    void organiser_validates_registrations_when_the_event_requires_it() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("rv-orga-" + n + "@example.bf");
        String eventId = publishedEvent(orga, TestAuth.adminToken(), true)[0];
        String user = TestAuth.registerAndToken("rv-user-" + n + "@example.bf", "PARTICULIER");

        String regId = as(user).body(Map.of("type", "PARTICULIER"))
                .when().post("/api/events/" + eventId + "/registrations")
                .then().statusCode(201).body("statut", equalTo("EN_ATTENTE"))
                .extract().path("id");

        as(orga).when().get("/api/events/" + eventId + "/registrations")
                .then().statusCode(200).body("totalElements", equalTo(1));

        as(orga).when().post("/api/registrations/" + regId + "/confirm")
                .then().statusCode(200).body("statut", equalTo("CONFIRMEE"));
    }

    @Test
    void registration_documents_can_be_uploaded_and_listed() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("rd-orga-" + n + "@example.bf");
        String eventId = publishedEvent(orga, TestAuth.adminToken(), false)[0];
        String user = TestAuth.registerAndToken("rd-user-" + n + "@example.bf", "PARTICULIER");

        String regId = as(user).body(Map.of("type", "PARTICULIER"))
                .when().post("/api/events/" + eventId + "/registrations")
                .then().statusCode(201).extract().path("id");

        byte[] pdf = "%PDF-1.4 fake".getBytes();
        given().header("Authorization", "Bearer " + user)
                .multiPart("file", "attestation.pdf", pdf, "application/pdf")
                .multiPart("type", "ATTESTATION")
                .when().post("/api/registrations/" + regId + "/documents")
                .then().statusCode(201)
                .body("nom", equalTo("attestation.pdf"))
                .body("url", notNullValue());

        given().header("Authorization", "Bearer " + user)
                .when().get("/api/registrations/" + regId + "/documents")
                .then().statusCode(200).body("$", hasSize(1));
    }
}
