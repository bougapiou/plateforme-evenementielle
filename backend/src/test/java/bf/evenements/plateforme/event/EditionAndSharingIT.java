package bf.evenements.plateforme.event;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** ORG-07 (next edition), PART-08 (link preview), STAT-04 (closing report), SEC-04 (programme manager). */
class EditionAndSharingIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    @Test
    void duplicate_share_report_and_delegate() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("ed-orga-" + n + "@example.bf");
        String eventId = as(orga).body(Map.of("nom", "SN 2026 " + n, "ville", "Ouagadougou", "lieu", "SIAO",
                        "hasActivities", true, "descriptionCourte", "Semaine du numérique",
                        "dateDebut", "2026-12-24T08:00:00Z", "dateFin", "2026-12-27T20:00:00Z"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");
        as(orga).body(Map.of("titre", "Panel IA", "acces", "GRATUIT", "salle", "Kilimandjaro", "capacite", 100,
                        "dateDebut", "2026-12-26T09:00:00Z", "dateFin", "2026-12-26T11:00:00Z"))
                .when().post("/api/events/" + eventId + "/activities").then().statusCode(201);
        as(orga).body(Map.of("nom", "Pass", "prixMontant", 2000, "portee", "EVENEMENT", "quantiteTotale", 300))
                .when().post("/api/events/" + eventId + "/tickets").then().statusCode(201);
        as(orga).body(Map.of("nom", "ARCEP", "niveau", "OR"))
                .when().post("/api/events/" + eventId + "/partners").then().statusCode(201);

        // ORG-07: one click → next year's draft with programme, tickets and partners.
        String copy = as(orga).when().post("/api/events/" + eventId + "/dupliquer")
                .then().statusCode(201)
                .body("nom", equalTo("SN 2027 " + n))
                .body("statut", equalTo("BROUILLON"))
                .body("dateDebut", equalTo("2027-12-24T08:00:00Z"))
                .extract().path("id");
        as(orga).when().get("/api/events/" + copy + "/activities").then().statusCode(200)
                .body("$", hasSize(1)).body("[0].dateDebut", equalTo("2027-12-26T09:00:00Z"))
                .body("[0].freeTicketId", org.hamcrest.Matchers.notNullValue());
        as(orga).when().get("/api/events/" + copy + "/tickets").then().statusCode(200)
                .body("findAll { it.nom == 'Pass' }", hasSize(1));
        as(orga).when().get("/api/events/" + copy + "/partners").then().statusCode(200).body("$", hasSize(1));

        // STAT-04: the closing report is a PDF.
        byte[] pdf = as(orga).when().get("/api/reports/events/" + eventId + "/bilan.pdf")
                .then().statusCode(200).extract().asByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        // SEC-04: a programme manager edits the programme of this event only.
        String mgrEmail = "ed-prog-" + n + "@example.bf";
        TestAuth.registerAndToken(mgrEmail, "PARTICULIER");
        as(orga).body(Map.of("email", mgrEmail, "fonction", "PROGRAMME"))
                .when().post("/api/events/" + eventId + "/staff").then().statusCode(201)
                .body("fonction", equalTo("PROGRAMME"));
        String mgr = TestAuth.login(mgrEmail, "Secret123!Pne");
        as(mgr).when().get("/api/events/mine").then().statusCode(200)
                .body("content.id", org.hamcrest.Matchers.hasItem(eventId));
        as(mgr).body(Map.of("titre", "Atelier", "dateDebut", "2026-12-25T09:00:00Z"))
                .when().post("/api/events/" + eventId + "/activities").then().statusCode(201);
        as(mgr).body(Map.of("titre", "Intrus", "dateDebut", "2027-12-25T09:00:00Z"))
                .when().post("/api/events/" + copy + "/activities").then().statusCode(403);

        // PART-08: the share link carries the Open Graph tags once published.
        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(TestAuth.adminToken()).when().post("/api/events/" + eventId + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/publish").then().statusCode(200);
        String slug = as(orga).when().get("/api/events/" + eventId).then().extract().path("slug");
        given().when().get("/partage/evenements/" + slug).then().statusCode(200)
                .body(containsString("og:title"))
                .body(containsString("SN 2026 " + n))
                .body(containsString("SIAO, Ouagadougou"));
    }
}
