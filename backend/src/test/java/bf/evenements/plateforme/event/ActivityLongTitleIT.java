package bf.evenements.plateforme.event;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Une activité gratuite au titre long (jusqu'à 200 caractères) ne doit pas casser son billet automatique. */
class ActivityLongTitleIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    @Test
    void a_free_activity_with_a_long_title_gets_a_shortened_ticket_name() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("long-orga-" + n + "@example.bf");
        String eventId = as(orga).body(Map.of("nom", "Titres longs " + n, "dateDebut", "2027-09-01T08:00:00Z",
                        "dateFin", "2027-09-03T18:00:00Z", "ville", "Ouagadougou"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");

        String titre = "Conférence – Infrastructures numériques souveraines et résilientes : bâtir les fondations "
                + "d’une transformation digitale sécurisée et prête pour l’intelligence artificielle";
        assertThat(titre.length()).isGreaterThan(150).isLessThanOrEqualTo(200);

        String activityId = as(orga).body(Map.of("titre", titre, "acces", "GRATUIT", "dateDebut", "2027-09-01T15:00:00Z"))
                .when().post("/api/events/" + eventId + "/activities").then().statusCode(201)
                .extract().path("id");

        List<Map<String, Object>> tickets = as(orga).when().get("/api/events/" + eventId + "/tickets")
                .then().statusCode(200).extract().jsonPath().getList("$");
        String nom = tickets.stream()
                .filter(t -> ((List<?>) t.get("activites")).toString().contains(activityId))
                .map(t -> (String) t.get("nom")).findFirst().orElseThrow();
        assertThat(nom).startsWith("Accès — Conférence").endsWith("…").hasSizeLessThanOrEqualTo(120);
    }
}
