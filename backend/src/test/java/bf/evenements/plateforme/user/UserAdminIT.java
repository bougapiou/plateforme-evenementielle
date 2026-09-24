package bf.evenements.plateforme.user;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UserAdminIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    @Test
    void admin_can_edit_and_delete_a_user_without_history_but_not_one_with_history() {
        long n = System.nanoTime();
        String admin = TestAuth.adminToken();

        // a fresh guest account has no history
        String phone = "+226 72" + (n % 1_000_000);
        String guestId = given().contentType(ContentType.JSON).body(Map.of("phone", phone))
                .when().post("/api/auth/guest-quick").then().statusCode(200).extract().path("user.id");

        // edit
        as(admin).body(Map.of("email", "edit-" + n + "@example.bf", "firstName", "Awa",
                        "lastName", "Ouedraogo", "phone", "+226 70 00 00 00"))
                .when().patch("/api/users/" + guestId)
                .then().statusCode(200)
                .body("firstName", equalTo("Awa"))
                .body("email", equalTo("edit-" + n + "@example.bf"));

        // e-mail already used by another account -> 409
        String orga = TestAuth.organizerToken("ua-orga-" + n + "@example.bf");
        as(admin).body(Map.of("email", "ua-orga-" + n + "@example.bf", "firstName", "A", "lastName", "B"))
                .when().patch("/api/users/" + guestId)
                .then().statusCode(409).body("code", equalTo("EMAIL_ALREADY_USED"));

        // a non-admin cannot edit or delete
        as(orga).when().delete("/api/users/" + guestId).then().statusCode(403);

        // delete the clean user
        as(admin).when().delete("/api/users/" + guestId).then().statusCode(204);
        as(admin).when().get("/api/users/" + guestId).then().statusCode(404);

        // an organiser who owns an event has history -> 409, suspend instead
        String orgaId = as(orga).when().get("/api/users/me").then().extract().path("id");
        as(orga).body(Map.of("nom", "Hist " + n,
                        "dateDebut", "2027-10-25T09:00:00Z", "dateFin", "2027-11-03T20:00:00Z",
                        "ville", "Ouagadougou"))
                .when().post("/api/events").then().statusCode(201);
        as(admin).when().delete("/api/users/" + orgaId)
                .then().statusCode(409).body("code", equalTo("USER_HAS_HISTORY"));
        as(admin).when().get("/api/users/" + orgaId + "/history")
                .then().statusCode(200).body("evenementsOrganises", equalTo(1));
        // even with force, an organiser of events cannot be deleted
        as(admin).when().delete("/api/users/" + orgaId + "?force=true")
                .then().statusCode(422).body("code", equalTo("ORGANIZER_HAS_EVENTS"));
        // ...and the failed delete left the account intact
        as(admin).when().get("/api/users/" + orgaId).then().statusCode(200);

        // self-delete forbidden
        String adminId = as(admin).when().get("/api/users/me").then().extract().path("id");
        as(admin).when().delete("/api/users/" + adminId)
                .then().statusCode(422).body("code", equalTo("SELF_DELETE_FORBIDDEN"));
    }

    @Test
    void admin_can_force_delete_a_buyer_with_history_and_the_sold_quota_is_given_back() {
        long n = System.nanoTime();
        String admin = TestAuth.adminToken();
        String orga = TestAuth.organizerToken("fd-orga-" + n + "@example.bf");
        String eventId = as(orga).body(Map.of("nom", "FD " + n,
                        "dateDebut", "2027-10-25T09:00:00Z", "dateFin", "2027-11-03T20:00:00Z",
                        "ville", "Ouagadougou"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");
        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + eventId + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/open-registrations").then().statusCode(200);
        String cat = as(orga).body(Map.of("nom", "Entrée", "prixMontant", 1000,
                        "portee", "EVENEMENT", "quantiteTotale", 10))
                .when().post("/api/events/" + eventId + "/tickets").then().statusCode(201)
                .extract().path("id");

        String buyer = TestAuth.registerAndToken("fd-buyer-" + n + "@example.bf", "PARTICULIER");
        String buyerId = as(buyer).when().get("/api/users/me").then().extract().path("id");
        String orderId = as(buyer).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", cat, "quantite", 2))))
                .when().post("/api/ticket-orders").then().statusCode(201).extract().path("id");
        as(buyer).when().post("/api/ticket-orders/" + orderId + "/pay-sandbox").then().statusCode(200);
        as(orga).when().get("/api/events/" + eventId + "/tickets")
                .then().statusCode(200).body("[0].quantiteVendue", equalTo(2));

        as(admin).when().get("/api/users/" + buyerId + "/history")
                .then().statusCode(200)
                .body("commandes", equalTo(1)).body("billets", equalTo(2))
                .body("paiements", equalTo(1)).body("factures", equalTo(2));

        // warning first ...
        as(admin).when().delete("/api/users/" + buyerId)
                .then().statusCode(409).body("code", equalTo("USER_HAS_HISTORY"));
        // ... then the confirmed deletion
        as(admin).when().delete("/api/users/" + buyerId + "?force=true").then().statusCode(204);
        as(admin).when().get("/api/users/" + buyerId).then().statusCode(404);

        // the quota sold to that buyer is available again
        as(orga).when().get("/api/events/" + eventId + "/tickets")
                .then().statusCode(200).body("[0].quantiteVendue", equalTo(0));
    }
}
