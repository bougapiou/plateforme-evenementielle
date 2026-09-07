package bf.evenements.plateforme.event;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventWorkflowIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    @Test
    void full_event_lifecycle_with_activities_then_public_visibility() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("orga-evt-" + n + "@example.bf");
        String admin = TestAuth.adminToken();

        String categoryId = given().when().get("/api/event-categories")
                .then().statusCode(200).extract().path("[0].id");

        Map<String, Object> body = new HashMap<>();
        body.put("nom", "Semaine du Numérique " + n);
        body.put("sigle", "SDN");
        body.put("descriptionCourte", "Rendez-vous national du numérique");
        body.put("categoryId", categoryId);
        body.put("dateDebut", "2027-11-01T08:00:00Z");
        body.put("dateFin", "2027-11-05T18:00:00Z");
        body.put("ville", "Ouagadougou");
        body.put("lieu", "Palais des sports");
        body.put("hasActivities", true);
        body.put("standsActifs", true);

        var created = as(orga).body(body).when().post("/api/events")
                .then().statusCode(201)
                .body("statut", equalTo("BROUILLON"))
                .body("hasActivities", equalTo(true))
                .body("slug", notNullValue())
                .extract().response();
        String eventId = created.path("id");
        String slug = created.path("slug");

        // programme : two activities
        as(orga).body(Map.of("titre", "Cérémonie d'ouverture", "typeActivite", "CEREMONIE",
                        "dateDebut", "2027-11-01T09:00:00Z", "salle", "Grand amphi"))
                .when().post("/api/events/" + eventId + "/activities")
                .then().statusCode(201);
        as(orga).body(Map.of("titre", "Panel : IA et services publics", "typeActivite", "PANEL",
                        "dateDebut", "2027-11-01T14:00:00Z", "moderateur", "M. Kaboré"))
                .when().post("/api/events/" + eventId + "/activities")
                .then().statusCode(201);

        as(orga).when().get("/api/events/" + eventId + "/activities")
                .then().statusCode(200)
                .body("$", hasSize(2))
                .body("titre", hasItem("Cérémonie d'ouverture"));

        // one speaker, one partner
        as(orga).body(Map.of("nom", "Dr. Awa Sawadogo", "titre", "Experte IA",
                        "organisation", "Université de Ouagadougou"))
                .when().post("/api/events/" + eventId + "/speakers").then().statusCode(201);
        as(orga).body(Map.of("nom", "Ministère de la Transition Numérique", "niveau", "PLATINE"))
                .when().post("/api/events/" + eventId + "/partners").then().statusCode(201);

        // cannot publish before validation
        as(orga).when().post("/api/events/" + eventId + "/publish")
                .then().statusCode(422).body("code", equalTo("INVALID_EVENT_TRANSITION"));

        // submit -> validate -> publish
        as(orga).when().post("/api/events/" + eventId + "/submit")
                .then().statusCode(200).body("statut", equalTo("SOUMIS"));
        as(admin).when().post("/api/events/" + eventId + "/validate")
                .then().statusCode(200).body("statut", equalTo("VALIDE"));
        as(orga).when().post("/api/events/" + eventId + "/publish")
                .then().statusCode(200).body("statut", equalTo("PUBLIE"));

        // public visibility
        given().when().get("/api/public/events?ville=Ouagadougou")
                .then().statusCode(200)
                .body("content.slug", hasItem(slug))
                .body("totalElements", greaterThanOrEqualTo(1));

        given().when().get("/api/public/events/" + slug)
                .then().statusCode(200)
                .body("nom", equalTo("Semaine du Numérique " + n))
                .body("hasActivities", equalTo(true))
                .body("programme", hasSize(2))
                .body("intervenants", hasSize(1))
                .body("partenaires", hasSize(1));
    }

    @Test
    void other_organizer_cannot_access_or_modify_event() {
        long n = System.nanoTime();
        String orgaA = TestAuth.organizerToken("orgaA-" + n + "@example.bf");
        String orgaB = TestAuth.organizerToken("orgaB-" + n + "@example.bf");

        String eventId = as(orgaA).body(Map.of(
                        "nom", "Forum privé " + n,
                        "dateDebut", "2027-06-01T08:00:00Z",
                        "dateFin", "2027-06-02T18:00:00Z",
                        "ville", "Bobo-Dioulasso"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");

        as(orgaB).when().get("/api/events/" + eventId).then().statusCode(403);
        as(orgaB).body(Map.of("nom", "Piraté", "dateDebut", "2027-06-01T08:00:00Z",
                        "dateFin", "2027-06-02T18:00:00Z"))
                .when().put("/api/events/" + eventId).then().statusCode(403);
    }

    @Test
    void admin_can_publish_and_open_registrations_on_a_validated_event() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("orga-adm-pub-" + n + "@example.bf");
        String admin = TestAuth.adminToken();

        String eventId = as(orga).body(Map.of(
                        "nom", "Salon régional " + n,
                        "descriptionCourte", "Salon des produits du terroir",
                        "dateDebut", "2027-09-01T08:00:00Z",
                        "dateFin", "2027-09-03T18:00:00Z",
                        "ville", "Fada N'Gourma"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");

        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(admin).when().post("/api/events/" + eventId + "/validate")
                .then().statusCode(200).body("statut", equalTo("VALIDE"));

        // the admin (not the owner) can drive publication and registrations
        as(admin).when().post("/api/events/" + eventId + "/publish")
                .then().statusCode(200).body("statut", equalTo("PUBLIE"));
        as(admin).when().post("/api/events/" + eventId + "/open-registrations")
                .then().statusCode(200).body("statut", equalTo("INSCRIPTIONS_OUVERTES"));
        as(admin).when().post("/api/events/" + eventId + "/close-registrations")
                .then().statusCode(200).body("statut", equalTo("INSCRIPTIONS_FERMEES"));
    }

    @Test
    void admin_can_reject_a_submitted_event_with_a_reason() {
        long n = System.nanoTime();
        String orga = TestAuth.organizerToken("orga-rej-" + n + "@example.bf");
        String admin = TestAuth.adminToken();

        String eventId = as(orga).body(Map.of(
                        "nom", "Événement incomplet " + n,
                        "dateDebut", "2027-03-01T08:00:00Z",
                        "dateFin", "2027-03-02T18:00:00Z",
                        "ville", "Koudougou"))
                .when().post("/api/events").then().statusCode(201).extract().path("id");

        as(orga).when().post("/api/events/" + eventId + "/submit").then().statusCode(200);
        as(admin).body(Map.of("motif", "Le programme et les tarifs doivent être précisés."))
                .when().post("/api/events/" + eventId + "/reject")
                .then().statusCode(200)
                .body("statut", equalTo("REFUSE"))
                .body("motifRefus", notNullValue());

        // organiser can edit again and resubmit
        as(orga).body(Map.of("nom", "Événement corrigé " + n,
                        "dateDebut", "2027-03-01T08:00:00Z", "dateFin", "2027-03-02T18:00:00Z",
                        "ville", "Koudougou"))
                .when().put("/api/events/" + eventId).then().statusCode(200);
        as(orga).when().post("/api/events/" + eventId + "/submit")
                .then().statusCode(200).body("statut", equalTo("SOUMIS"));
    }
}
