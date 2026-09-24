package bf.evenements.plateforme.loadtest;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.utility.MountableFile;

/**
 * Validates infra/loadtest/cleanup-*.sql against a real PostgreSQL with the real schema —
 * that script is irreversible on the production database, so it is tested here.
 */
class LoadTestCleanupIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private ExecResult psql(String file) throws Exception {
        Path host = Path.of("..", "infra", "loadtest", file).toAbsolutePath().normalize();
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(host), "/tmp/" + file);
        return POSTGRES.execInContainer("psql", "-U", POSTGRES.getUsername(), "-d",
                POSTGRES.getDatabaseName(), "-v", "ON_ERROR_STOP=1", "-1", "-f", "/tmp/" + file);
    }

    private String publishedEvent(String orga, String admin, String nom) {
        String id = as(orga).body(Map.of("nom", nom,
                        "dateDebut", "2027-10-25T09:00:00Z", "dateFin", "2027-11-03T20:00:00Z",
                        "ville", "Ouagadougou"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");
        as(orga).when().post("/api/events/" + id + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + id + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + id + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + id + "/open-registrations").then().statusCode(200);
        return id;
    }

    private String freeCategory(String orga, String eventId) {
        return as(orga).body(Map.of("nom", "Gratuit", "prixMontant", 0, "portee", "EVENEMENT",
                        "quantiteTotale", 100, "formulaireRequis", false))
                .when().post("/api/events/" + eventId + "/tickets").then().statusCode(201)
                .extract().path("id");
    }

    /** Free ticket as a phone-only guest, like the k6 script. Returns the guest's user id. */
    private String guestTakesTicket(String phone, String eventId, String category) {
        var session = given().contentType(ContentType.JSON).body(Map.of("phone", phone))
                .when().post("/api/auth/guest-quick").then().statusCode(200).extract().response();
        String token = session.path("accessToken");
        as(token).body(Map.of("type", "PARTICULIER",
                        "participants", List.of(Map.of("nom", "Visiteur")),
                        "tickets", List.of(Map.of("eventTicketId", category, "quantite", 1))))
                .when().post("/api/events/" + eventId + "/registrations")
                .then().statusCode(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(200), org.hamcrest.Matchers.is(201)));
        return session.path("user.id");
    }

    /** Scans the guest's ticket QR as the organiser (a real check-in on the test event). */
    private String scanGuestTicket(String phone, String orga, String eventId) throws Exception {
        var session = given().contentType(ContentType.JSON).body(Map.of("phone", phone))
                .when().post("/api/auth/guest-quick").then().statusCode(200).extract().response();
        String token = session.path("accessToken");
        String ticketId = as(token).when().get("/api/tickets/my").then().statusCode(200)
                .extract().path("[0].id");
        byte[] png = given().header("Authorization", "Bearer " + token)
                .when().get("/api/tickets/" + ticketId + "/qr.png").then().statusCode(200)
                .extract().asByteArray();
        String qr = bf.evenements.plateforme.support.QrTestUtil.decode(png);
        as(orga).body(Map.of("token", qr, "eventId", eventId, "sens", "ENTREE"))
                .when().post("/api/checkins/scan").then().statusCode(200)
                .body("resultat", org.hamcrest.Matchers.equalTo("VALIDE"));
        return qr;
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    void cleanup_removes_only_test_data_and_refuses_when_real_users_are_on_the_test_event()
            throws Exception {
        long n = System.nanoTime();
        String admin = TestAuth.adminToken();
        String orga = TestAuth.organizerToken("lt-orga-" + n + "@example.bf");

        // A normal event with a real (non-guest) buyer and a non-marker guest: must survive.
        String realEvent = publishedEvent(orga, admin, "Salon réel " + n);
        String realCat = freeCategory(orga, realEvent);
        String realUser = TestAuth.registerAndToken("lt-real-" + n + "@example.bf", "PARTICULIER");
        String realUserId = as(realUser).when().get("/api/users/me").then().extract().path("id");
        as(realUser).body(Map.of("type", "PARTICULIER",
                        "participants", List.of(Map.of("nom", "Vrai")),
                        "tickets", List.of(Map.of("eventTicketId", realCat, "quantite", 1))))
                .when().post("/api/events/" + realEvent + "/registrations").then().statusCode(201);
        String realGuestId = guestTakesTicket("+226 70" + (n % 1_000_000), realEvent, realCat);

        // --- Guard: a real user on the TEST event => the script must abort and delete nothing.
        String testEvent1 = publishedEvent(orga, admin, "ZZ-TEST-CHARGE A " + n);
        String cat1 = freeCategory(orga, testEvent1);
        String g1 = guestTakesTicket("+226 99999" + (n % 10_000_000), testEvent1, cat1);
        String intruder = TestAuth.registerAndToken("lt-intruder-" + n + "@example.bf", "PARTICULIER");
        as(intruder).body(Map.of("type", "PARTICULIER",
                        "participants", List.of(Map.of("nom", "Intrus")),
                        "tickets", List.of(Map.of("eventTicketId", cat1, "quantite", 1))))
                .when().post("/api/events/" + testEvent1 + "/registrations").then().statusCode(201);

        ExecResult refused = psql("cleanup-2-suppression.sql");
        assertThat(refused.getExitCode()).as(refused.getStderr()).isNotZero();
        assertThat(refused.getStderr()).contains("vrais utilisateurs");
        assertThat(count("select count(*) from users where id = ?::uuid", g1)).isEqualTo(1);
        // take that event out of the marker so the next run is about the real cleanup
        jdbc.update("update events set nom = 'ARCHIVE ' || nom where id = ?::uuid", testEvent1);

        // --- Real cleanup
        String testEvent2 = publishedEvent(orga, admin, "ZZ-TEST-CHARGE B " + n);
        String cat2 = freeCategory(orga, testEvent2);
        String g2 = guestTakesTicket("+226 99999" + ((n + 1) % 10_000_000), testEvent2, cat2);
        String g3 = guestTakesTicket("+226 99999" + ((n + 2) % 10_000_000), testEvent2, cat2);
        assertThat(count("select count(*) from tickets where event_id = ?::uuid", testEvent2)).isEqualTo(2);

        // a real check-in and a sensor with passages on the test event: removed with the event
        String qr = scanGuestTicket("+226 99999" + ((n + 1) % 10_000_000), orga, testEvent2);
        String sensorKey = as(orga).body(Map.of("nom", "Laser test"))
                .when().post("/api/events/" + testEvent2 + "/sensors").then().statusCode(201)
                .extract().path("cle");
        given().header("X-Sensor-Key", sensorKey)
                .when().post("/api/sensors/entry?count=3").then().statusCode(200);
        assertThat(count("select count(*) from checkins where event_id = ?::uuid", testEvent2)).isEqualTo(1);
        assertThat(count("select count(*) from passages_capteur where event_id = ?::uuid", testEvent2)).isEqualTo(1);

        // export-tokens.sql gives the k6 scan script its input: the token decoded from the QR
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(
                Path.of("..", "infra", "loadtest", "export-tokens.sql").toAbsolutePath().normalize()),
                "/tmp/export-tokens.sql");
        ExecResult export = POSTGRES.execInContainer("sh", "-c", "cd /tmp && psql -U "
                + POSTGRES.getUsername() + " -d " + POSTGRES.getDatabaseName()
                + " -v ON_ERROR_STOP=1 -f /tmp/export-tokens.sql && cat /tmp/tokens.csv");
        assertThat(export.getExitCode()).as(export.getStderr()).isZero();
        assertThat(export.getStdout()).contains(qr);

        ExecResult preview = psql("cleanup-1-apercu.sql");
        assertThat(preview.getExitCode()).as(preview.getStderr()).isZero();

        ExecResult done = psql("cleanup-2-suppression.sql");
        assertThat(done.getExitCode()).as(done.getStderr()).isZero();

        // test data gone (incl. the guest left over from the refused run) ...
        for (String gone : List.of(g1, g2, g3)) {
            assertThat(count("select count(*) from users where id = ?::uuid", gone)).isZero();
        }
        assertThat(count("select count(*) from events where id = ?::uuid", testEvent2)).isZero();
        assertThat(count("select count(*) from tickets where event_id = ?::uuid", testEvent2)).isZero();
        assertThat(count("select count(*) from checkins where event_id = ?::uuid", testEvent2)).isZero();
        assertThat(count("select count(*) from capteurs where event_id = ?::uuid", testEvent2)).isZero();
        assertThat(count("select count(*) from passages_capteur where event_id = ?::uuid", testEvent2)).isZero();
        assertThat(count("select count(*) from users where phone like '+226 99999%'")).isZero();
        // ... real data untouched
        assertThat(count("select count(*) from users where id = ?::uuid", realUserId)).isEqualTo(1);
        assertThat(count("select count(*) from users where id = ?::uuid", realGuestId)).isEqualTo(1);
        assertThat(count("select count(*) from events where id = ?::uuid", realEvent)).isEqualTo(1);
        assertThat(count("select count(*) from tickets where event_id = ?::uuid", realEvent)).isEqualTo(2);
        assertThat(count("select count(*) from events where id = ?::uuid", testEvent1)).isEqualTo(1);
    }
}
