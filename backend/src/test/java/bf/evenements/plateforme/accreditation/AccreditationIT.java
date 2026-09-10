package bf.evenements.plateforme.accreditation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.QrTestUtil;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AccreditationIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private String qrOf(String token, String accreditationId) throws Exception {
        byte[] png = given().header("Authorization", "Bearer " + token)
                .when().get("/api/accreditations/" + accreditationId + "/qr.png")
                .then().statusCode(200).extract().asByteArray();
        return QrTestUtil.decode(png);
    }

    @Test
    void issue_badges_scoped_to_an_activity_or_the_whole_event() throws Exception {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("acc-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();

        String eventId = as(orga).body(Map.of("nom", "FESPACO " + n,
                        "dateDebut", "2027-02-25T09:00:00Z", "dateFin", "2027-03-04T20:00:00Z",
                        "ville", "Ouagadougou", "hasActivities", true))
                .when().post("/api/events").then().statusCode(201).extract().path("id");
        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + eventId + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/publish").then().statusCode(200);

        String panel = as(orga).body(Map.of("titre", "Panel cinéma africain",
                        "dateDebut", "2027-02-26T10:00:00Z"))
                .when().post("/api/events/" + eventId + "/activities").then().statusCode(201)
                .extract().path("id");
        String projection = as(orga).body(Map.of("titre", "Projection d'ouverture",
                        "dateDebut", "2027-02-25T19:00:00Z"))
                .when().post("/api/events/" + eventId + "/activities").then().statusCode(201)
                .extract().path("id");

        // badge for the panel only
        String panelist = as(orga).body(Map.of("personneNom", "Awa Kaboré",
                        "organisation", "Cinémathèque", "fonction", "PANELISTE", "activityId", panel))
                .when().post("/api/events/" + eventId + "/accreditations").then().statusCode(201)
                .body("numero", notNullValue())
                .body("fonctionLibelle", equalTo("Panéliste"))
                .extract().path("id");

        // event-wide press badge
        String press = as(orga).body(Map.of("personneNom", "Ibrahim Sana",
                        "organisation", "RTB", "fonction", "PRESSE"))
                .when().post("/api/events/" + eventId + "/accreditations").then().statusCode(201)
                .extract().path("id");

        as(orga).when().get("/api/events/" + eventId + "/accreditations")
                .then().statusCode(200).body("size()", equalTo(2));

        // the badge PDF renders
        as(orga).when().get("/api/accreditations/" + panelist + "/badge.pdf")
                .then().statusCode(200).contentType("application/pdf");

        String panelToken = qrOf(orga, panelist);
        String pressToken = qrOf(orga, press);

        // panelist badge is valid at the panel, invalid elsewhere
        as(orga).body(Map.of("token", panelToken, "eventId", eventId, "activityId", panel))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("VALIDE"))
                .body("participantNom", equalTo("Awa Kaboré"));
        as(orga).body(Map.of("token", panelToken, "eventId", eventId, "activityId", projection))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("INVALIDE"));

        // press badge works for any activity and allows re-entry
        as(orga).body(Map.of("token", pressToken, "eventId", eventId, "activityId", panel))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("VALIDE"));
        as(orga).body(Map.of("token", pressToken, "eventId", eventId, "activityId", projection))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("VALIDE"));
        as(orga).body(Map.of("token", pressToken, "eventId", eventId, "activityId", projection))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("VALIDE")); // badges are not single-use

        // revoked badge is refused
        as(orga).when().post("/api/accreditations/" + press + "/revoke")
                .then().statusCode(200).body("statut", equalTo("REVOQUEE"));
        as(orga).body(Map.of("token", pressToken, "eventId", eventId))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", equalTo("INVALIDE"))
                .body("message", org.hamcrest.Matchers.containsString("révoqué"));

        // a participant cannot issue accreditations
        String someone = TestAuth.registerAndToken("acc-x-" + n + "@example.bf", "PARTICULIER");
        as(someone).body(Map.of("personneNom", "X", "fonction", "STAFF"))
                .when().post("/api/events/" + eventId + "/accreditations").then().statusCode(403);
    }

    @Test
    void autre_role_requires_a_free_text_label() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("acc2-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();
        String eventId = as(orga).body(Map.of("nom", "Salon " + n,
                        "dateDebut", "2027-06-01T09:00:00Z", "dateFin", "2027-06-03T18:00:00Z",
                        "ville", "Bobo"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");
        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + eventId + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/publish").then().statusCode(200);

        as(orga).body(Map.of("personneNom", "Y", "fonction", "AUTRE"))
                .when().post("/api/events/" + eventId + "/accreditations")
                .then().statusCode(422).body("code", equalTo("FONCTION_LIBRE_REQUISE"));

        as(orga).body(Map.of("personneNom", "Y", "fonction", "AUTRE", "fonctionLibre", "Bénévole"))
                .when().post("/api/events/" + eventId + "/accreditations")
                .then().statusCode(201).body("fonctionLibelle", equalTo("Bénévole"));
    }
}
