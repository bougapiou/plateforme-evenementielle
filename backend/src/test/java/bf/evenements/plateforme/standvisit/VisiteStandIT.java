package bf.evenements.plateforme.standvisit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VisiteStandIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private String[] publishedEventWithOneStand(String orga, String admin, long n) {
        var res = as(orga).body(Map.of(
                        "nom", "Salon " + n,
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
        as(orga).body(Map.of("nom", "Stand Standard", "prixMontant", 100000, "quantiteTotale", 1))
                .when().post("/api/events/" + id + "/stand-types").then().statusCode(201);
        var stand = given().when().get("/api/public/events/" + slug + "/stands")
                .then().statusCode(200).body("$", hasSize(1)).extract().response();
        return new String[] {id, slug, stand.path("[0].id"), stand.path("[0].numero")};
    }

    @Test
    void visitor_signals_a_passage_with_or_without_identity_and_the_ranking_updates() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("vs-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();
        String[] ev = publishedEventWithOneStand(orga, admin, n);
        String slug = ev[1];
        String standId = ev[2];
        String numero = ev[3];

        // the stand page, before anyone has signalled anything
        given().when().get("/api/public/events/" + slug + "/stands/" + standId + "/passage")
                .then().statusCode(200)
                .body("numero", equalTo(numero))
                .body("standTypeNom", equalTo("Stand Standard"))
                .body("exposantNom", nullValue()); // no reservation yet

        // no login needed, and no identity required at all
        given().contentType(ContentType.JSON).body("{}")
                .when().post("/api/public/events/" + slug + "/stands/" + standId + "/passage")
                .then().statusCode(201);

        // a second visitor, with identity this time
        given().contentType(ContentType.JSON)
                .body(Map.of("nom", "Ouédraogo", "prenom", "Awa", "telephone", "+226 70000000"))
                .when().post("/api/public/events/" + slug + "/stands/" + standId + "/passage")
                .then().statusCode(201);

        // an invalid phone is rejected
        given().contentType(ContentType.JSON).body(Map.of("telephone", "pas-un-numero"))
                .when().post("/api/public/events/" + slug + "/stands/" + standId + "/passage")
                .then().statusCode(400);

        // the public ranking reflects both real visits, split identified / anonymous
        given().when().get("/api/public/events/" + slug + "/frequentation")
                .then().statusCode(200)
                .body("totalVisites", equalTo(2))
                .body("totalIdentifiees", equalTo(1))
                .body("totalAnonymes", equalTo(1))
                .body("stands", hasSize(1))
                .body("stands[0].visites", equalTo(2))
                .body("stands[0].visitesIdentifiees", equalTo(1))
                .body("stands[0].visitesAnonymes", equalTo(1))
                .body("stands[0].numero", equalTo(numero));
    }

    private String[] publishedEventWithStands(String orga, String admin, long n, int quantite) {
        var res = as(orga).body(Map.of(
                        "nom", "Salon " + n,
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
        as(orga).body(Map.of("nom", "Stand Standard", "prixMontant", 100000, "quantiteTotale", quantite))
                .when().post("/api/events/" + id + "/stand-types").then().statusCode(201);
        return new String[] {id, slug};
    }

    @Test
    void visitor_signals_several_stands_at_once_and_per_stand_stats_are_available() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("vsb-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();
        String[] ev = publishedEventWithStands(orga, admin, n, 3);
        String slug = ev[1];

        var stands = given().when().get("/api/public/events/" + slug + "/stands")
                .then().statusCode(200).body("$", hasSize(3)).extract().response();
        String stand1 = stands.path("[0].id");
        String stand2 = stands.path("[1].id");
        String stand3 = stands.path("[2].id");

        // ticking two stands at once, with a first name only
        given().contentType(ContentType.JSON)
                .body(Map.of("standIds", java.util.List.of(stand1, stand2), "prenom", "Awa"))
                .when().post("/api/public/events/" + slug + "/passages")
                .then().statusCode(201).body("enregistres", equalTo(2));

        // no stand selected at all -> rejected before it reaches the service
        given().contentType(ContentType.JSON).body(Map.of("standIds", java.util.List.of()))
                .when().post("/api/public/events/" + slug + "/passages")
                .then().statusCode(400);

        given().when().get("/api/public/events/" + slug + "/stands/" + stand1 + "/frequentation")
                .then().statusCode(200)
                .body("visites", equalTo(1))
                .body("visitesIdentifiees", equalTo(1))
                .body("visitesAnonymes", equalTo(0));

        given().when().get("/api/public/events/" + slug + "/stands/" + stand3 + "/frequentation")
                .then().statusCode(200)
                .body("visites", equalTo(0))
                .body("visitesIdentifiees", equalTo(0))
                .body("visitesAnonymes", equalTo(0));

        given().when().get("/api/public/events/" + slug + "/frequentation")
                .then().statusCode(200)
                .body("totalVisites", equalTo(2))
                .body("totalIdentifiees", equalTo(2))
                .body("totalAnonymes", equalTo(0))
                .body("stands", hasSize(3));
    }

    @Test
    void stand_page_shows_the_exhibitor_once_their_reservation_is_confirmed() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("vs2-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();
        String[] ev = publishedEventWithOneStand(orga, admin, n);
        String eventId = ev[0];
        String slug = ev[1];
        String standId = ev[2];

        String structToken = TestAuth.registerAndToken("vs2-struct-" + n + "@example.bf", "STRUCTURE");
        String structureId = as(structToken)
                .body(Map.of("raisonSociale", "Faso Artisanat " + n, "typeStructure", "ENTREPRISE"))
                .when().post("/api/structures").then().statusCode(201).extract().path("id");
        as(admin).body(Map.of("statut", "VERIFIEE"))
                .when().patch("/api/structures/" + structureId + "/status").then().statusCode(200);
        structToken = TestAuth.login("vs2-struct-" + n + "@example.bf", "Secret123");

        String reservationId = as(structToken)
                .body(Map.of("eventId", eventId, "standId", standId, "structureId", structureId))
                .when().post("/api/stand-reservations").then().statusCode(201).extract().path("id");
        as(structToken).when().post("/api/stand-reservations/" + reservationId + "/pay-sandbox")
                .then().statusCode(200);

        given().when().get("/api/public/events/" + slug + "/stands/" + standId + "/passage")
                .then().statusCode(200)
                .body("exposantNom", equalTo("Faso Artisanat " + n));
    }

    @Test
    void unknown_stand_is_a_404() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("vs3-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();
        String slug = publishedEventWithOneStand(orga, admin, n)[1];

        given().when().get("/api/public/events/" + slug + "/stands/" + java.util.UUID.randomUUID() + "/passage")
                .then().statusCode(404);
    }
}
