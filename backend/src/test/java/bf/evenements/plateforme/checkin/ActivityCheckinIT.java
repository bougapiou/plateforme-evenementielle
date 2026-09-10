package bf.evenements.plateforme.checkin;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.QrTestUtil;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Access control scoped to a single activity of a multi-session event. */
class ActivityCheckinIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private String qrToken(String ownerToken, String ticketId) throws Exception {
        byte[] png = given().header("Authorization", "Bearer " + ownerToken)
                .when().get("/api/tickets/" + ticketId + "/qr.png")
                .then().statusCode(200).extract().asByteArray();
        return QrTestUtil.decode(png);
    }

    @Test
    void free_activity_ticket_is_valid_only_for_its_activity() throws Exception {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("ac-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();

        String eventId = as(orga).body(Map.of("nom", "Semaine du Numérique " + n,
                        "dateDebut", "2027-10-25T09:00:00Z", "dateFin", "2027-10-30T18:00:00Z",
                        "ville", "Ouagadougou", "hasActivities", true))
                .when().post("/api/events").then().statusCode(201).extract().path("id");
        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + eventId + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/open-registrations").then().statusCode(200);

        String actA = as(orga).body(Map.of("titre", "Atelier IA", "acces", "GRATUIT",
                        "dateDebut", "2027-10-26T09:00:00Z"))
                .when().post("/api/events/" + eventId + "/activities").then().statusCode(201)
                .body("acces", equalTo("GRATUIT"))
                .extract().path("id");
        String actB = as(orga).body(Map.of("titre", "Conférence Cloud", "acces", "PAYANT",
                        "dateDebut", "2027-10-27T09:00:00Z"))
                .when().post("/api/events/" + eventId + "/activities").then().statusCode(201)
                .extract().path("id");

        // a controller sees both activities for this event
        as(orga).when().get("/api/checkins/events/" + eventId + "/activities")
                .then().statusCode(200)
                .body("titre", hasItem("Atelier IA"))
                .body("titre", hasItem("Conférence Cloud"));

        // --- visitor joins the free activity -> gets a QR ticket ---
        String visitor = TestAuth.registerAndToken("ac-visitor-" + n + "@example.bf", "PARTICULIER");
        String ticketId = as(visitor).when().post("/api/activities/" + actA + "/attend")
                .then().statusCode(201)
                .body("numero", org.hamcrest.Matchers.notNullValue())
                .extract().path("id");

        // joining twice is refused (limit 1 per person)
        as(visitor).when().post("/api/activities/" + actA + "/attend")
                .then().statusCode(422).body("code", equalTo("PER_USER_LIMIT"));

        String token = qrToken(visitor, ticketId);

        // valid at activity A
        as(orga).body(Map.of("token", token, "eventId", eventId, "activityId", actA))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("VALIDE"))
                .body("activiteNom", equalTo("Atelier IA"));

        // re-scan at A -> already used
        as(orga).body(Map.of("token", token, "eventId", eventId, "activityId", actA))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("DEJA_UTILISE"));

        // this ticket does not grant access to activity B
        as(orga).body(Map.of("token", token, "eventId", eventId, "activityId", actB))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("INVALIDE"))
                .body("message", org.hamcrest.Matchers.containsString("activité"));

        // --- an event-wide pass gets into every activity ---
        String catPass = as(orga).body(Map.of("nom", "Pass complet", "prixMontant", 5000,
                        "portee", "EVENEMENT", "quantiteTotale", 50))
                .when().post("/api/events/" + eventId + "/tickets").then().statusCode(201)
                .extract().path("id");
        String buyer = TestAuth.registerAndToken("ac-pass-" + n + "@example.bf", "PARTICULIER");
        String orderId = as(buyer).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", catPass, "quantite", 1))))
                .when().post("/api/ticket-orders").then().statusCode(201).extract().path("id");
        as(buyer).when().post("/api/ticket-orders/" + orderId + "/pay-sandbox").then().statusCode(200);
        String passTicket = as(buyer).when().get("/api/tickets/my").then().statusCode(200)
                .extract().path("[0].id");
        String passToken = qrToken(buyer, passTicket);

        as(orga).body(Map.of("token", passToken, "eventId", eventId, "activityId", actA))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("VALIDE"));
        as(orga).body(Map.of("token", passToken, "eventId", eventId, "activityId", actB))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("VALIDE"));
    }

    @Test
    void entries_and_exits_are_counted_per_activity() throws Exception {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("ace-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();

        String eventId = as(orga).body(Map.of("nom", "FESPACO " + n,
                        "dateDebut", "2027-02-25T09:00:00Z", "dateFin", "2027-03-04T18:00:00Z",
                        "ville", "Ouagadougou", "hasActivities", true))
                .when().post("/api/events").then().statusCode(201)
                .extract().path("id");
        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + eventId + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/open-registrations").then().statusCode(200);

        String projection = as(orga).body(Map.of("titre", "Projection en plein air", "acces", "PAYANT",
                        "dateDebut", "2027-02-26T20:00:00Z"))
                .when().post("/api/events/" + eventId + "/activities").then().statusCode(201)
                .extract().path("id");

        String cat = as(orga).body(Map.of("nom", "Pass festival", "prixMontant", 3000,
                        "portee", "EVENEMENT", "quantiteTotale", 50))
                .when().post("/api/events/" + eventId + "/tickets").then().statusCode(201)
                .extract().path("id");
        String buyer = TestAuth.registerAndToken("ace-buyer-" + n + "@example.bf", "PARTICULIER");
        String orderId = as(buyer).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", cat, "quantite", 1))))
                .when().post("/api/ticket-orders").then().statusCode(201).extract().path("id");
        as(buyer).when().post("/api/ticket-orders/" + orderId + "/pay-sandbox").then().statusCode(200);
        String ticketId = as(buyer).when().get("/api/tickets/my").then().statusCode(200)
                .extract().path("[0].id");
        String token = qrToken(buyer, ticketId);

        // ENTREE into the activity
        as(orga).body(Map.of("token", token, "eventId", eventId, "activityId", projection, "sens", "ENTREE"))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("VALIDE")).body("sens", equalTo("ENTREE"))
                .body("reentree", equalTo(false));
        // ENTREE again -> already inside the activity
        as(orga).body(Map.of("token", token, "eventId", eventId, "activityId", projection, "sens", "ENTREE"))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("DEJA_UTILISE"));
        // SORTIE
        as(orga).body(Map.of("token", token, "eventId", eventId, "activityId", projection, "sens", "SORTIE"))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("VALIDE")).body("sens", equalTo("SORTIE"));
        // ENTREE -> re-entry
        as(orga).body(Map.of("token", token, "eventId", eventId, "activityId", projection, "sens", "ENTREE"))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("VALIDE")).body("reentree", equalTo(true));

        // per-activity flow
        as(orga).when().get("/api/events/" + eventId + "/checkin-stats?activityId=" + projection)
                .then().statusCode(200)
                .body("entrees", equalTo(2))
                .body("sorties", equalTo(1))
                .body("presents", equalTo(1))
                .body("reentrees", equalTo(1));

        // attendance view lists the activity with its flow
        as(orga).when().get("/api/events/" + eventId + "/attendance")
                .then().statusCode(200)
                .body("activites[0].titre", equalTo("Projection en plein air"))
                .body("activites[0].flux.entrees", equalTo(2))
                .body("activites[0].flux.presents", equalTo(1));
    }

    @Test
    void attend_is_refused_for_a_non_free_activity() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("acn-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();
        String eventId = as(orga).body(Map.of("nom", "Forum " + n,
                        "dateDebut", "2027-11-01T09:00:00Z", "dateFin", "2027-11-02T18:00:00Z",
                        "ville", "Bobo", "hasActivities", true))
                .when().post("/api/events").then().statusCode(201).extract().path("id");
        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + eventId + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/open-registrations").then().statusCode(200);

        String act = as(orga).body(Map.of("titre", "Plénière", "dateDebut", "2027-11-01T10:00:00Z"))
                .when().post("/api/events/" + eventId + "/activities").then().statusCode(201)
                .body("acces", equalTo("SANS_BILLET"))
                .extract().path("id");

        String visitor = TestAuth.registerAndToken("acn-v-" + n + "@example.bf", "PARTICULIER");
        as(visitor).when().post("/api/activities/" + act + "/attend")
                .then().statusCode(422).body("code", equalTo("ACTIVITY_NOT_FREE"));
    }
}
