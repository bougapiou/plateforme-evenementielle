package bf.evenements.plateforme.stand;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StandReservationIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private String[] publishedEventWithStands(String orga, String admin) {
        long n = System.nanoTime();
        var res = as(orga).body(Map.of(
                        "nom", "Foire " + n,
                        "dateDebut", "2027-10-01T08:00:00Z",
                        "dateFin", "2027-10-10T18:00:00Z",
                        "ville", "Ouagadougou",
                        "standsActifs", true))
                .when().post("/api/events").then().statusCode(201).extract().response();
        String id = res.path("id");
        String slug = res.path("slug");
        as(orga).when().post("/api/events/" + id + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + id + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + id + "/publish").then().statusCode(200);
        return new String[] {id, slug};
    }

    /** A STRUCTURE-role user who created a structure (still EN_ATTENTE). Returns [token, structureId]. */
    private String[] structureUser(String email) {
        String token = TestAuth.registerAndToken(email, "STRUCTURE");
        String structureId = as(token)
                .body(Map.of("raisonSociale", "Ese " + email, "typeStructure", "ENTREPRISE"))
                .when().post("/api/structures").then().statusCode(201).extract().path("id");
        return new String[] {TestAuth.login(email, "Secret123"), structureId};
    }

    /** Same, but the structure has been verified by an admin. */
    private String[] verifiedStructureUser(String email) {
        String[] u = structureUser(email);
        as(TestAuth.adminToken()).body(Map.of("statut", "VERIFIEE"))
                .when().patch("/api/structures/" + u[1] + "/status").then().statusCode(200);
        return new String[] {TestAuth.login(email, "Secret123"), u[1]};
    }

    @Test
    void reserve_hold_then_pay_and_double_reservation_is_blocked() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("s-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();
        String[] ev = publishedEventWithStands(orga, admin);
        String eventId = ev[0];
        String slug = ev[1];

        as(orga).body(Map.of("nom", "Stand Standard", "prixMontant", 250000, "quantiteTotale", 3))
                .when().post("/api/events/" + eventId + "/stand-types").then().statusCode(201);

        given().when().get("/api/public/events/" + slug + "/stand-types")
                .then().statusCode(200)
                .body("[0].quantiteRestante", equalTo(3));

        String standId = given().when().get("/api/public/events/" + slug + "/stands")
                .then().statusCode(200).body("$", hasSize(3))
                .body("[0].disponible", equalTo(true))
                .extract().path("[0].id");

        String[] structA = verifiedStructureUser("structA-" + n + "@example.bf");
        String reservationId = as(structA[0])
                .body(Map.of("eventId", eventId, "standId", standId, "structureId", structA[1]))
                .when().post("/api/stand-reservations")
                .then().statusCode(201)
                .body("statut", equalTo("RESERVE_TEMP"))
                .body("montant", equalTo(250000.0f))
                .extract().path("id");

        // another structure cannot grab the same stand
        String[] structB = verifiedStructureUser("structB-" + n + "@example.bf");
        as(structB[0]).body(Map.of("eventId", eventId, "standId", standId, "structureId", structB[1]))
                .when().post("/api/stand-reservations")
                .then().statusCode(409).body("code", equalTo("STAND_ALREADY_RESERVED"));

        // pay -> confirmed
        as(structA[0]).when().post("/api/stand-reservations/" + reservationId + "/pay-sandbox")
                .then().statusCode(200).body("statut", equalTo("CONFIRME"));

        // stand no longer available publicly, restante drops
        given().when().get("/api/public/events/" + slug + "/stand-types")
                .then().statusCode(200).body("[0].quantiteRestante", equalTo(2));
        given().when().get("/api/public/events/" + slug + "/stands")
                .then().statusCode(200)
                .body("find { it.id == '" + standId + "' }.disponible", equalTo(false));

        // organiser sees the reservation
        as(orga).when().get("/api/stand-reservations/for-event/" + eventId)
                .then().statusCode(200).body("totalElements", equalTo(1));
    }

    @Test
    void cancelling_a_temp_reservation_frees_the_stand() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("sc-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();
        String[] ev = publishedEventWithStands(orga, admin);
        String eventId = ev[0];
        String slug = ev[1];

        as(orga).body(Map.of("nom", "Premium", "prixMontant", 500000, "quantiteTotale", 1))
                .when().post("/api/events/" + eventId + "/stand-types").then().statusCode(201);
        String standId = given().when().get("/api/public/events/" + slug + "/stands")
                .then().statusCode(200).extract().path("[0].id");

        String[] structA = verifiedStructureUser("scA-" + n + "@example.bf");
        String resId = as(structA[0])
                .body(Map.of("eventId", eventId, "standId", standId, "structureId", structA[1]))
                .when().post("/api/stand-reservations").then().statusCode(201).extract().path("id");

        String[] structB = verifiedStructureUser("scB-" + n + "@example.bf");
        as(structB[0]).body(Map.of("eventId", eventId, "standId", standId, "structureId", structB[1]))
                .when().post("/api/stand-reservations").then().statusCode(409);

        as(structA[0]).when().post("/api/stand-reservations/" + resId + "/cancel")
                .then().statusCode(200).body("statut", equalTo("ANNULE"));

        as(structB[0]).body(Map.of("eventId", eventId, "standId", standId, "structureId", structB[1]))
                .when().post("/api/stand-reservations").then().statusCode(201);
    }

    @Test
    void free_stand_reservation_is_confirmed_immediately() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("sf-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();
        String[] ev = publishedEventWithStands(orga, admin);

        as(orga).body(Map.of("nom", "Institutionnel", "prixMontant", 0, "quantiteTotale", 2))
                .when().post("/api/events/" + ev[0] + "/stand-types").then().statusCode(201);
        String standId = given().when().get("/api/public/events/" + ev[1] + "/stands")
                .then().statusCode(200).extract().path("[0].id");

        String[] struct = verifiedStructureUser("sf-" + n + "@example.bf");
        as(struct[0]).body(Map.of("eventId", ev[0], "standId", standId, "structureId", struct[1]))
                .when().post("/api/stand-reservations")
                .then().statusCode(201).body("statut", equalTo("CONFIRME"));
    }

    @Test
    void a_stand_requires_a_verified_structure() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("sv-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();
        String[] ev = publishedEventWithStands(orga, admin);

        as(orga).body(Map.of("nom", "Standard", "prixMontant", 100000, "quantiteTotale", 2))
                .when().post("/api/events/" + ev[0] + "/stand-types").then().statusCode(201);
        String standId = given().when().get("/api/public/events/" + ev[1] + "/stands")
                .then().statusCode(200).extract().path("[0].id");

        String[] struct = structureUser("sv-" + n + "@example.bf"); // structure still EN_ATTENTE

        // no structure at all -> refused
        as(struct[0]).body(Map.of("eventId", ev[0], "standId", standId))
                .when().post("/api/stand-reservations")
                .then().statusCode(422).body("code", equalTo("STRUCTURE_REQUIRED"));

        // unverified structure -> refused
        as(struct[0]).body(Map.of("eventId", ev[0], "standId", standId, "structureId", struct[1]))
                .when().post("/api/stand-reservations")
                .then().statusCode(422).body("code", equalTo("STRUCTURE_NOT_VERIFIED"));

        // admin verifies -> now allowed
        as(admin).body(Map.of("statut", "VERIFIEE"))
                .when().patch("/api/structures/" + struct[1] + "/status").then().statusCode(200);
        as(struct[0]).body(Map.of("eventId", ev[0], "standId", standId, "structureId", struct[1]))
                .when().post("/api/stand-reservations")
                .then().statusCode(201).body("statut", equalTo("RESERVE_TEMP"));
    }
}
