package bf.evenements.plateforme.auth;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import bf.evenements.plateforme.audit.DataRetentionJob;
import bf.evenements.plateforme.notification.EmailSender;
import bf.evenements.plateforme.notification.SmsSender;
import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

/** PART-04 (« Mes billets » with an SMS code), SEC-05 (immutable audit), SEC-08 (anonymisation). */
class OtpAndRetentionIT extends AbstractIntegrationTest {

    @MockBean
    SmsSender smsSender;

    @MockBean
    EmailSender emailSender;

    @Autowired
    DataRetentionJob retentionJob;

    @Autowired
    JdbcTemplate jdbc;

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    /** A guest (phone only) takes a free ticket on a fresh published event; returns the event id. */
    private String guestWithTicket(String phone) {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("otp-orga-" + n + "@example.bf");
        String eventId = as(orga).body(Map.of("nom", "Forum " + n, "ville", "Bobo",
                        "dateDebut", "2027-03-01T08:00:00Z", "dateFin", "2027-03-02T18:00:00Z"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");
        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(TestAuth.adminToken()).when().post("/api/events/" + eventId + "/validate").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/publish").then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/open-registrations").then().statusCode(200);
        String cat = as(orga).body(Map.of("nom", "Entrée libre", "prixMontant", 0, "portee", "EVENEMENT",
                        "quantiteTotale", 50, "formulaireRequis", false))
                .when().post("/api/events/" + eventId + "/tickets").then().statusCode(201).extract().path("id");
        String guest = given().contentType(ContentType.JSON).body(Map.of("phone", phone))
                .when().post("/api/auth/guest-quick").then().statusCode(200).extract().path("accessToken");
        as(guest).body(Map.of("eventId", eventId, "lignes", List.of(Map.of("eventTicketId", cat, "quantite", 1))))
                .when().post("/api/ticket-orders").then().statusCode(201);
        return eventId;
    }

    @Test
    void tickets_are_found_with_a_code_received_by_sms() {
        String phone = "+226 72" + (System.nanoTime() % 1_000_000);
        guestWithTicket(phone);

        given().contentType(ContentType.JSON).body(Map.of("phone", phone))
                .when().post("/api/auth/otp/request").then().statusCode(202);
        ArgumentCaptor<String> sms = ArgumentCaptor.forClass(String.class);
        verify(smsSender).send(eq(phone), sms.capture());
        Matcher m = Pattern.compile("(\\d{6})").matcher(sms.getValue());
        assertThat(m.find()).isTrue();
        String code = m.group(1);

        // A wrong code fails; the right one opens the guest session holding the ticket.
        given().contentType(ContentType.JSON).body(Map.of("phone", phone, "code", "000000"))
                .when().post("/api/auth/otp/verify").then().statusCode(422).body("code", equalTo("OTP_INVALID"));
        String token = given().contentType(ContentType.JSON).body(Map.of("phone", phone, "code", code))
                .when().post("/api/auth/otp/verify").then().statusCode(200)
                .body("accessToken", notNullValue()).extract().path("accessToken");
        as(token).when().get("/api/tickets/my").then().statusCode(200).body("size()", equalTo(1));

        // A code is single use.
        given().contentType(ContentType.JSON).body(Map.of("phone", phone, "code", code))
                .when().post("/api/auth/otp/verify").then().statusCode(422);

        // Unknown number: same answer, but no SMS is sent.
        String other = "+226 73" + (System.nanoTime() % 1_000_000);
        given().contentType(ContentType.JSON).body(Map.of("phone", other))
                .when().post("/api/auth/otp/request").then().statusCode(202);
        verify(smsSender, never()).send(eq(other), anyString());
    }

    @Test
    void finished_events_are_anonymised_and_the_audit_log_cannot_be_altered() {
        String phone = "+226 74" + (System.nanoTime() % 1_000_000);
        String eventId = guestWithTicket(phone);

        // Only this event (the test database is shared by every test class).
        retentionJob.anonymiseEvent(java.util.UUID.fromString(eventId));
        assertThat(jdbc.queryForObject("select count(*) from users where phone = ?", Long.class, phone)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from tickets where event_id = ?::uuid", Long.class, eventId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select donnees_anonymisees_le from events where id = ?::uuid",
                java.sql.Timestamp.class, eventId)).isNotNull();

        // SEC-05: the audit trail refuses updates and deletes.
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> jdbc.update("update audit_logs set action = 'X' where action = 'DATA_ANONYMIZED'"))
                .hasMessageContaining("pas modifiable");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("delete from audit_logs"))
                .hasMessageContaining("pas modifiable");
    }
}
