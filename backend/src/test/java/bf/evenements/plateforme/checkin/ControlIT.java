package bf.evenements.plateforme.checkin;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.QrTestUtil;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Field control: several free sessions in one registration (PART-03), on-site
 * registration (CTRL-03), offline sync without duplicates (CTRL-01), agents
 * restricted to their rooms (CTRL-05) and the per-activity figures (P1-05).
 */
class ControlIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private String freeSession(String orga, String eventId, String titre, String salle, int capacite) {
        return as(orga).body(Map.of("titre", titre, "acces", "GRATUIT", "salle", salle, "capacite", capacite,
                        "dateDebut", "2027-11-26T09:00:00Z", "dateFin", "2027-11-26T12:00:00Z"))
                .when().post("/api/events/" + eventId + "/activities")
                .then().statusCode(201).extract().path("id");
    }

    private String freeTicketOf(String orga, String eventId, String activityId) {
        return as(orga).when().get("/api/events/" + eventId + "/activities")
                .then().statusCode(200).extract().jsonPath()
                .getString("find { it.id == '" + activityId + "' }.freeTicketId");
    }

    @Test
    void sessions_on_site_offline_sync_rooms_and_activity_figures() throws Exception {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("ctl-orga-" + n + "@example.bf");
        String admin = TestAuth.adminToken();
        String eventId = as(orga).body(Map.of("nom", "SN " + n, "hasActivities", true, "ville", "Ouagadougou",
                        "dateDebut", "2027-11-24T08:00:00Z", "dateFin", "2027-11-27T20:00:00Z"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");
        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + eventId + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/open-registrations").then().statusCode(200);

        String panel = freeSession(orga, eventId, "Conf'Day 3", "Kilimandjaro", 10);
        String atelier = freeSession(orga, eventId, "Atelier IA", "Salle B", 10);
        String forum = freeSession(orga, eventId, "Forum", "Salle C", 10);

        // PART-03: three free sessions, one order, three tickets.
        String visitor = TestAuth.registerAndToken("ctl-visit-" + n + "@example.bf", "PARTICULIER");
        as(visitor).body(Map.of("eventId", eventId, "lignes", List.of(
                        Map.of("eventTicketId", freeTicketOf(orga, eventId, panel), "quantite", 1),
                        Map.of("eventTicketId", freeTicketOf(orga, eventId, atelier), "quantite", 1),
                        Map.of("eventTicketId", freeTicketOf(orga, eventId, forum), "quantite", 1))))
                .when().post("/api/ticket-orders").then().statusCode(201).body("statut", equalTo("PAYEE"));
        JsonPath mine = as(visitor).when().get("/api/tickets/my").then().statusCode(200)
                .body("$", hasSize(3)).extract().jsonPath();
        String panelTicket = mine.getString("find { it.categorieNom.contains(\"Conf'Day 3\") }.id");
        String token = QrTestUtil.decode(given().header("Authorization", "Bearer " + visitor)
                .when().get("/api/tickets/" + panelTicket + "/qr.png").then().statusCode(200)
                .extract().asByteArray());

        // CTRL-05: an agent restricted to the « Atelier IA » room.
        String agentEmail = "ctl-agent-" + n + "@example.bf";
        TestAuth.registerAndToken(agentEmail, "PARTICULIER");
        String agentUserId = as(orga).body(Map.of("email", agentEmail))
                .when().post("/api/events/" + eventId + "/staff").then().statusCode(201)
                .extract().path("userId");
        as(orga).body(Map.of("activityIds", List.of(atelier)))
                .when().put("/api/events/" + eventId + "/staff/" + agentUserId + "/activities")
                .then().statusCode(200);
        String agent = TestAuth.login(agentEmail, "Secret123!Pne");
        as(agent).when().get("/api/checkins/events/" + eventId + "/activities")
                .then().statusCode(200).body("$", hasSize(1)).body("[0].id", equalTo(atelier));
        as(agent).body(Map.of("token", token, "eventId", eventId, "activityId", panel))
                .when().post("/api/checkins/scan").then().statusCode(403);

        // CTRL-01: the offline pack holds hashes, not tokens; a replayed sync is not counted twice.
        as(orga).when().get("/api/checkins/events/" + eventId + "/offline-pack")
                .then().statusCode(200)
                .body("billets.find { it.h == '" + ControlService.sha256(token) + "' }.numero", notNullValue());
        Map<String, Object> offline = Map.of("token", token, "eventId", eventId, "activityId", panel,
                "sens", "ENTREE", "clientRef", "dev1-" + n, "scannedAt", Instant.now().minusSeconds(120).toString());
        as(orga).body(List.of(offline)).when().post("/api/checkins/sync")
                .then().statusCode(200).body("[0].resultat", equalTo("VALIDE"))
                .body("[0].dejaSynchronise", equalTo(false));
        as(orga).body(List.of(offline)).when().post("/api/checkins/sync")
                .then().statusCode(200).body("[0].dejaSynchronise", equalTo(true));

        // CTRL-03: an unregistered visitor is registered and let in at the door.
        as(orga).body(Map.of("activityId", panel, "nom", "Issa Kaboré", "telephone", "+226 76 55 44 33"))
                .when().post("/api/checkins/events/" + eventId + "/sur-place")
                .then().statusCode(201)
                .body("numeroBillet", notNullValue())
                .body("scan.resultat", equalTo("VALIDE"));

        // P1-05: registered, present, rate, on-site, capacity, fill rate for the panel.
        JsonPath report = as(orga).when().get("/api/reports/events/" + eventId + "/stats")
                .then().statusCode(200).extract().jsonPath();
        String line = "parActivite.find { it.id == '" + panel + "' }";
        org.assertj.core.api.Assertions.assertThat(report.getInt(line + ".inscrits")).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(report.getInt(line + ".inscritsSurPlace")).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(report.getInt(line + ".visiteursUniques")).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(report.getDouble(line + ".tauxPresence")).isEqualTo(100.0);
        org.assertj.core.api.Assertions.assertThat(report.getDouble(line + ".tauxRemplissage")).isEqualTo(20.0);

        // STAT-02: the table exports as CSV with a BOM (accents in Excel).
        byte[] csv = as(orga).when().get("/api/reports/events/" + eventId + "/stats/activites/export?format=CSV")
                .then().statusCode(200).extract().asByteArray();
        org.assertj.core.api.Assertions.assertThat(new String(csv, java.nio.charset.StandardCharsets.UTF_8))
                .startsWith("﻿Activité;").contains("Conf'Day 3");
    }
}
