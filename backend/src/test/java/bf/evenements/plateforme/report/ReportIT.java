package bf.evenements.plateforme.report;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ReportIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private void passage(String eventId, String capteurId, String sens, int nombre, String at) {
        Timestamp ts = Timestamp.from(Instant.parse(at));
        jdbc.update("insert into passages_capteur (id, version, created_at, updated_at, event_id, capteur_id,"
                        + " sens, nombre, passe_le) values (?, 0, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), ts, ts, UUID.fromString(eventId), UUID.fromString(capteurId), sens, nombre, ts);
    }

    @Test
    void day_and_event_stats_sensor_flow_and_participant_exports() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("rp-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();

        String eventId = as(orga).body(Map.of("nom", "Foire " + n,
                        "dateDebut", "2027-10-25T08:00:00Z", "dateFin", "2027-10-27T18:00:00Z",
                        "ville", "Ouagadougou"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");
        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + eventId + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/open-registrations").then().statusCode(200);
        String cat = as(orga).body(Map.of("nom", "Standard", "prixMontant", 1000,
                        "portee", "EVENEMENT", "quantiteTotale", 50))
                .when().post("/api/events/" + eventId + "/tickets").then().statusCode(201)
                .extract().path("id");

        // One buyer takes 3 tickets: 3 rows, but a single person once duplicates are removed.
        String buyer = TestAuth.registerAndToken("rp-buyer-" + n + "@example.bf", "PARTICULIER");
        String orderId = as(buyer).body(Map.of("eventId", eventId,
                        "lignes", List.of(Map.of("eventTicketId", cat, "quantite", 3))))
                .when().post("/api/ticket-orders").then().statusCode(201).extract().path("id");
        as(buyer).when().post("/api/ticket-orders/" + orderId + "/pay-sandbox").then().statusCode(200);

        String capteurId = as(orga).body(Map.of("nom", "Porte A"))
                .when().post("/api/events/" + eventId + "/sensors").then().statusCode(201)
                .extract().path("capteur.id");
        passage(eventId, capteurId, "ENTREE", 7, "2027-10-25T09:10:00Z");
        passage(eventId, capteurId, "ENTREE", 3, "2027-10-25T10:40:00Z");
        passage(eventId, capteurId, "SORTIE", 4, "2027-10-25T10:55:00Z");
        passage(eventId, capteurId, "ENTREE", 5, "2027-10-26T14:00:00Z");

        String base = "/api/reports/events/" + eventId;

        // --- stats: whole event, then a single day
        as(admin).when().get(base + "/stats")
                .then().statusCode(200)
                .body("jours", hasSize(3))
                .body("totaux.billetsEmis", equalTo(3))
                .body("totaux.revenus", equalTo(3000f))
                .body("totaux.capteurEntrees", equalTo(15))
                .body("totaux.capteurSorties", equalTo(4))
                .body("parJour", hasSize(3))
                .body("parJour[0].valeurs.capteurEntrees", equalTo(10))
                .body("parJour[1].valeurs.capteurEntrees", equalTo(5));
        as(orga).when().get(base + "/stats?jour=2027-10-26")
                .then().statusCode(200)
                .body("jour", equalTo("2027-10-26"))
                .body("totaux.capteurEntrees", equalTo(5))
                .body("totaux.billetsEmis", equalTo(0));

        // --- flux per day, then per hour of the first day
        as(orga).when().get(base + "/flux?granularite=JOUR")
                .then().statusCode(200)
                .body("points", hasSize(3))
                .body("points[0].entrees", equalTo(10))
                .body("points[0].presents", equalTo(6))
                .body("points[1].presents", equalTo(11))
                .body("totalEntrees", equalTo(15))
                .body("capteurs[0].nom", equalTo("Porte A"));
        as(orga).when().get(base + "/flux?granularite=HEURE&jour=2027-10-25")
                .then().statusCode(200)
                .body("points", hasSize(24))
                .body("points[9].periode", equalTo("09h"))
                .body("points[9].entrees", equalTo(7))
                .body("points[10].entrees", equalTo(3))
                .body("points[10].sorties", equalTo(4))
                .body("points[10].presents", equalTo(6))
                .body("pic.periode", equalTo("09h"));

        // --- participants: with and without duplicates
        as(orga).when().get(base + "/participants?source=BILLETS&doublons=true")
                .then().statusCode(200)
                .body("total", equalTo(3))
                .body("uniques", equalTo(1))
                .body("doublons", equalTo(2))
                .body("lignes", hasSize(3));
        as(orga).when().get(base + "/participants?source=BILLETS&doublons=false")
                .then().statusCode(200)
                .body("lignes", hasSize(1))
                .body("lignes[0].occurrences", equalTo(3));
        // an interval that excludes everything
        as(orga).when().get(base + "/participants?source=BILLETS&du=2020-01-01T00:00&au=2020-01-02T00:00")
                .then().statusCode(200).body("total", equalTo(0));
        // an activity list cannot come from registrations
        as(orga).when().get(base + "/participants?perimetre=ACTIVITE&cibleId=" + UUID.randomUUID()
                        + "&source=INSCRITS")
                .then().statusCode(422);

        // --- exports: real files in the three formats
        byte[] xlsx = as(admin).when().get(base + "/participants/export?format=XLSX&doublons=false")
                .then().statusCode(200)
                .header("Content-Disposition", startsWith("attachment"))
                .extract().asByteArray();
        assertThat(new String(xlsx, 0, 2)).isEqualTo("PK");
        byte[] docx = as(admin).when().get(base + "/participants/export?format=DOCX")
                .then().statusCode(200).extract().asByteArray();
        assertThat(new String(docx, 0, 2)).isEqualTo("PK");
        byte[] pdf = as(admin).when().get(base + "/participants/export?format=PDF")
                .then().statusCode(200).extract().asByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

        // a buyer has no access to reports
        as(buyer).when().get(base + "/stats").then().statusCode(403);
    }
}
