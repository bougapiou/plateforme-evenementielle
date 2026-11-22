package bf.evenements.plateforme.event;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** ORG-01 (programme import), ORG-05 (room conflicts), PART-07 (multi-day session kept as one). */
class ProgrammeImportIT extends AbstractIntegrationTest {

    @Test
    void csv_import_reports_bad_lines_and_creates_the_others() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("imp-orga-" + n + "@example.bf");
        String eventId = given().header("Authorization", "Bearer " + orga).contentType(ContentType.JSON)
                .body(Map.of("nom", "SN import " + n, "ville", "Ouagadougou", "hasActivities", true,
                        "dateDebut", "2027-11-24T08:00:00Z", "dateFin", "2027-11-27T20:00:00Z"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");

        String csv = "﻿Titre;Type;Début;Fin;Salle;Intervenants;Modérateur;Description;Thème;Accès;Capacité\n"
                + "Conf'Day 3;Panel;26/11/2027 09:30;26/11/2027 12:00;Kilimandjaro;A. Ouédraogo;B. Traoré;"
                + "\"Résumé; avec point-virgule\";IA;Gratuit;250\n"
                + "SITICO;Exposition;24/11/2027 08:00;27/11/2027 18:00;Hall;;;Salon;;Sans billet;\n"
                + "Atelier sans date;Atelier;;;Salle B;;;;;;\n"
                + "Démo drones;Démonstration;26/11/2027 10:00;26/11/2027 11:00;kilimandjaro;;;;;;\n";
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        // Dry run: nothing created, the bad line is reported.
        given().header("Authorization", "Bearer " + orga)
                .multiPart("fichier", "programme.csv", bytes, "text/csv")
                .when().post("/api/events/" + eventId + "/activities/import?simulation=true")
                .then().statusCode(200)
                .body("total", equalTo(4)).body("valides", equalTo(3)).body("importees", equalTo(0))
                .body("lignes[2].ligne", equalTo(4))
                .body("lignes[2].erreurs[0]", equalTo("Début manquant"));
        given().header("Authorization", "Bearer " + orga)
                .when().get("/api/events/" + eventId + "/activities").then().statusCode(200).body("$", hasSize(0));

        // Real run.
        given().header("Authorization", "Bearer " + orga)
                .multiPart("fichier", "programme.csv", bytes, "text/csv")
                .when().post("/api/events/" + eventId + "/activities/import?simulation=false")
                .then().statusCode(200).body("importees", equalTo(3));
        given().header("Authorization", "Bearer " + orga)
                .when().get("/api/events/" + eventId + "/activities").then().statusCode(200)
                .body("$", hasSize(3))
                .body("find { it.titre == \"Conf'Day 3\" }.theme", equalTo("IA"))
                .body("find { it.titre == \"Conf'Day 3\" }.description", equalTo("Résumé; avec point-virgule"))
                .body("find { it.titre == \"Conf'Day 3\" }.freeTicketId", notNullValue())
                .body("find { it.titre == 'Démo drones' }.typeActivite", equalTo("DEMONSTRATION"));

        // Same room (case ignored), overlapping slots.
        given().header("Authorization", "Bearer " + orga)
                .when().get("/api/events/" + eventId + "/activities/conflicts").then().statusCode(200)
                .body("$", hasSize(1));

        // The template downloads.
        given().header("Authorization", "Bearer " + orga)
                .when().get("/api/events/" + eventId + "/activities/import/modele.xlsx").then().statusCode(200);
    }
}
