package bf.evenements.plateforme.ticket;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TicketingIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    /** Creates a published event with open registrations and returns its id + slug. */
    private String[] publishedEvent(String orga, String admin) {
        long n = System.nanoTime();
        var res = as(orga).body(Map.of(
                        "nom", "Salon " + n,
                        "dateDebut", "2027-09-01T08:00:00Z",
                        "dateFin", "2027-09-03T18:00:00Z",
                        "ville", "Ouagadougou"))
                .when().post("/api/events").then().statusCode(201).extract().response();
        String id = res.path("id");
        String slug = res.path("slug");
        as(orga).when().post("/api/events/" + id + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + id + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + id + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + id + "/open-registrations").then().statusCode(200);
        return new String[] {id, slug};
    }

    @Test
    void quota_and_per_user_limit_are_enforced_then_payment_issues_tickets() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("t-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();
        String[] ev = publishedEvent(orga, admin);
        String eventId = ev[0];
        String slug = ev[1];

        String standardId = as(orga).body(Map.of(
                        "nom", "Standard", "prixMontant", 5000, "portee", "EVENEMENT",
                        "quantiteTotale", 3, "limiteParUtilisateur", 2))
                .when().post("/api/events/" + eventId + "/tickets")
                .then().statusCode(201).extract().path("id");
        as(orga).body(Map.of("nom", "VIP", "prixMontant", 15000, "portee", "EVENEMENT",
                        "quantiteTotale", 1))
                .when().post("/api/events/" + eventId + "/tickets").then().statusCode(201);

        // public tarifs
        given().when().get("/api/public/events/" + slug + "/tickets")
                .then().statusCode(200).body("$", hasSize(2));

        // buyer 1 : 2 standard
        String buyer1 = TestAuth.registerAndToken("buyer1-" + n + "@example.bf", "PARTICULIER");
        String order1 = as(buyer1).body(Map.of(
                        "eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", standardId, "quantite", 2))))
                .when().post("/api/ticket-orders")
                .then().statusCode(201)
                .body("statut", equalTo("EN_ATTENTE"))
                .body("montantTotal", equalTo(10000.0f))
                .extract().path("id");

        // buyer 1 exceeds per-user limit
        as(buyer1).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", standardId, "quantite", 1))))
                .when().post("/api/ticket-orders")
                .then().statusCode(422).body("code", equalTo("PER_USER_LIMIT"));

        // buyer 2 : only 1 left
        String buyer2 = TestAuth.registerAndToken("buyer2-" + n + "@example.bf", "PARTICULIER");
        as(buyer2).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", standardId, "quantite", 2))))
                .when().post("/api/ticket-orders")
                .then().statusCode(422).body("code", equalTo("QUOTA_EXCEEDED"));
        as(buyer2).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", standardId, "quantite", 1))))
                .when().post("/api/ticket-orders").then().statusCode(201);

        // pay order 1 (sandbox) -> tickets issued
        as(buyer1).when().post("/api/ticket-orders/" + order1 + "/pay-sandbox")
                .then().statusCode(200).body("statut", equalTo("PAYEE"));

        as(buyer1).when().get("/api/tickets/my")
                .then().statusCode(200).body("$", hasSize(2))
                .body("statut", equalTo(List.of("EMISE", "EMISE")));

        // quota now exhausted
        as(orga).when().get("/api/events/" + eventId + "/tickets")
                .then().statusCode(200)
                .body("find { it.nom == 'Standard' }.quantiteVendue", equalTo(2))
                .body("find { it.nom == 'Standard' }.quantiteRestante", equalTo(0));

        // organiser sees the orders
        as(orga).when().get("/api/ticket-orders/for-event/" + eventId)
                .then().statusCode(200)
                .body("totalElements", greaterThanOrEqualTo(2));
    }

    @Test
    void free_ticket_order_is_confirmed_immediately() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("free-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();
        String eventId = publishedEvent(orga, admin)[0];

        String freeId = as(orga).body(Map.of("nom", "Invitation", "prixMontant", 0,
                        "portee", "EVENEMENT", "quantiteTotale", 50))
                .when().post("/api/events/" + eventId + "/tickets")
                .then().statusCode(201).extract().path("id");

        String buyer = TestAuth.registerAndToken("free-buyer-" + n + "@example.bf", "PARTICULIER");
        as(buyer).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", freeId, "quantite", 1))))
                .when().post("/api/ticket-orders")
                .then().statusCode(201).body("statut", equalTo("PAYEE"));
    }

    @Test
    void only_one_free_ticket_per_person_and_per_event() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("f1-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();
        String eventId = publishedEvent(orga, admin)[0];

        String freeA = as(orga).body(Map.of("nom", "Invitation A", "prixMontant", 0,
                        "portee", "EVENEMENT", "quantiteTotale", 50))
                .when().post("/api/events/" + eventId + "/tickets").then().statusCode(201)
                .body("limiteParUtilisateur", equalTo(1))
                .extract().path("id");
        String freeB = as(orga).body(Map.of("nom", "Invitation B", "prixMontant", 0,
                        "portee", "EVENEMENT", "quantiteTotale", 50))
                .when().post("/api/events/" + eventId + "/tickets").then().statusCode(201)
                .extract().path("id");
        String paid = as(orga).body(Map.of("nom", "Pass payant", "prixMontant", 3000,
                        "portee", "EVENEMENT", "quantiteTotale", 50))
                .when().post("/api/events/" + eventId + "/tickets").then().statusCode(201)
                .extract().path("id");

        String buyer = TestAuth.registerAndToken("f1-buyer-" + n + "@example.bf", "PARTICULIER");

        // first free ticket, and a request for 3 is capped to 1
        as(buyer).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", freeA, "quantite", 3))))
                .when().post("/api/ticket-orders").then().statusCode(201);

        // a second free ticket (other category, same event) is refused
        as(buyer).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", freeB, "quantite", 1))))
                .when().post("/api/ticket-orders")
                .then().statusCode(422).body("code", equalTo("FREE_TICKET_LIMIT"));

        // a paid ticket is still allowed
        as(buyer).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", paid, "quantite", 2))))
                .when().post("/api/ticket-orders").then().statusCode(201);
    }

    @Test
    void cancelling_a_pending_order_releases_the_quota() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("cancel-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();
        String eventId = publishedEvent(orga, admin)[0];

        String tId = as(orga).body(Map.of("nom", "Pass", "prixMontant", 2000,
                        "portee", "EVENEMENT", "quantiteTotale", 1))
                .when().post("/api/events/" + eventId + "/tickets")
                .then().statusCode(201).extract().path("id");

        String buyerA = TestAuth.registerAndToken("cancelA-" + n + "@example.bf", "PARTICULIER");
        String order = as(buyerA).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", tId, "quantite", 1))))
                .when().post("/api/ticket-orders").then().statusCode(201).extract().path("id");

        String buyerB = TestAuth.registerAndToken("cancelB-" + n + "@example.bf", "PARTICULIER");
        as(buyerB).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", tId, "quantite", 1))))
                .when().post("/api/ticket-orders").then().statusCode(422);

        as(buyerA).when().post("/api/ticket-orders/" + order + "/cancel")
                .then().statusCode(200).body("statut", equalTo("ANNULEE"));

        as(buyerB).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", tId, "quantite", 1))))
                .when().post("/api/ticket-orders").then().statusCode(201);
    }

    @Test
    void formulaire_requis_is_settable_when_free_but_forced_true_when_paid() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("form-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();
        String eventId = publishedEvent(orga, admin)[0];

        // free + no form
        as(orga).body(Map.of("nom", "Entrée libre", "prixMontant", 0,
                        "portee", "EVENEMENT", "quantiteTotale", 100, "formulaireRequis", false))
                .when().post("/api/events/" + eventId + "/tickets")
                .then().statusCode(201).body("formulaireRequis", equalTo(false));

        // paid: formulaireRequis is forced true even if the request says false
        as(orga).body(Map.of("nom", "VIP", "prixMontant", 5000,
                        "portee", "EVENEMENT", "quantiteTotale", 10, "formulaireRequis", false))
                .when().post("/api/events/" + eventId + "/tickets")
                .then().statusCode(201).body("formulaireRequis", equalTo(true));
    }
}
