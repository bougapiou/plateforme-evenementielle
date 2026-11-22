package bf.evenements.plateforme.user;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import bf.evenements.plateforme.support.TestAuth;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Gestion des comptes par l'administrateur : création, mot de passe, rôles et garde-fous. */
class UserProvisioningIT extends AbstractIntegrationTest {

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private int login(String email, String password) {
        return given().contentType(ContentType.JSON).body(Map.of("email", email, "password", password))
                .when().post("/api/auth/login").then().extract().statusCode();
    }

    @Test
    void admin_creates_users_with_typed_or_generated_password() {
        long n = System.nanoTime();
        String admin = TestAuth.adminToken();

        // mot de passe saisi, rôle par défaut du type
        as(admin).body(Map.of("email", "Prov-" + n + "@Example.bf", "firstName", "Awa", "lastName", "Kaboré",
                        "type", "PARTICULIER", "password", "Secret!2026"))
                .when().post("/api/users").then().statusCode(201)
                .body("user.email", equalTo("prov-" + n + "@example.bf"))
                .body("user.roles", equalTo(List.of("PARTICIPANT")))
                .body("motDePasseTemporaire", nullValue());
        assertThat(login("prov-" + n + "@example.bf", "Secret!2026")).isEqualTo(200);

        // mot de passe généré : renvoyé une seule fois, et il fonctionne
        String generated = as(admin).body(Map.of("email", "gen-" + n + "@example.bf", "firstName", "Issa",
                        "lastName", "Traoré", "type", "PERSONNEL"))
                .when().post("/api/users").then().statusCode(201)
                .body("user.roles", equalTo(List.of("PERSONNEL_CONTROLE")))
                .body("motDePasseTemporaire", notNullValue())
                .extract().path("motDePasseTemporaire");
        assertThat(generated).matches("[A-Za-z2-9]{4}-[A-Za-z2-9]{4}-[A-Za-z2-9]{4}");
        assertThat(login("gen-" + n + "@example.bf", generated)).isEqualTo(200);

        // e-mail déjà pris, mot de passe trop court
        as(admin).body(Map.of("email", "gen-" + n + "@example.bf", "firstName", "X", "lastName", "Y"))
                .when().post("/api/users").then().statusCode(409).body("code", equalTo("EMAIL_ALREADY_USED"));
        as(admin).body(Map.of("email", "short-" + n + "@example.bf", "firstName", "X", "lastName", "Y",
                        "password", "abc"))
                .when().post("/api/users").then().statusCode(422).body("code", equalTo("WEAK_PASSWORD"));

        // un non-admin ne peut pas créer de compte
        String orga = TestAuth.organizerToken("prov-orga-" + n + "@example.bf");
        as(orga).body(Map.of("email", "nope-" + n + "@example.bf", "firstName", "X", "lastName", "Y"))
                .when().post("/api/users").then().statusCode(403);
    }

    @Test
    void an_organiser_created_by_the_admin_can_create_events_right_away() {
        long n = System.nanoTime();
        String admin = TestAuth.adminToken();
        String pwd = as(admin).body(Map.of("email", "neworga-" + n + "@example.bf", "firstName", "Mariam",
                        "lastName", "Sawadogo", "type", "ORGANISATEUR", "organisationNom", "Comité FILO " + n))
                .when().post("/api/users").then().statusCode(201)
                .body("user.roles", hasItem("ORGANISATEUR"))
                .extract().path("motDePasseTemporaire");

        String token = TestAuth.login("neworga-" + n + "@example.bf", pwd);
        as(token).when().get("/api/organizers/me").then().statusCode(200)
                .body("nomAffichage", equalTo("Comité FILO " + n)).body("statut", equalTo("ACTIF"));
        as(token).body(Map.of("nom", "Salon " + n, "dateDebut", "2027-10-25T09:00:00Z",
                        "dateFin", "2027-10-26T18:00:00Z", "ville", "Ouagadougou"))
                .when().post("/api/events").then().statusCode(201);
    }

    @Test
    void admin_sets_a_password_and_the_user_is_signed_out_everywhere() {
        long n = System.nanoTime();
        String admin = TestAuth.adminToken();
        String email = "pwd-" + n + "@example.bf";
        String id = as(admin).body(Map.of("email", email, "firstName", "A", "lastName", "B", "password", "Ancien!2026"))
                .when().post("/api/users").then().statusCode(201).extract().path("user.id");
        String refresh = given().contentType(ContentType.JSON).body(Map.of("email", email, "password", "Ancien!2026"))
                .when().post("/api/auth/login").then().statusCode(200).extract().path("refreshToken");

        // mot de passe saisi
        as(admin).body(Map.of("password", "Nouveau!2026"))
                .when().post("/api/users/" + id + "/password").then().statusCode(200)
                .body("motDePasseTemporaire", nullValue());
        assertThat(login(email, "Ancien!2026")).isEqualTo(401);
        assertThat(login(email, "Nouveau!2026")).isEqualTo(200);
        // l'ancienne session ne peut plus être prolongée
        given().contentType(ContentType.JSON).body(Map.of("refreshToken", refresh))
                .when().post("/api/auth/refresh").then().statusCode(401);

        // mot de passe généré
        String generated = as(admin).body(Map.of())
                .when().post("/api/users/" + id + "/password").then().statusCode(200)
                .extract().path("motDePasseTemporaire");
        assertThat(login(email, generated)).isEqualTo(200);
    }

    @Test
    void admin_edits_type_and_roles_with_safeguards() {
        long n = System.nanoTime();
        String admin = TestAuth.adminToken();
        String adminId = as(admin).when().get("/api/users/me").then().extract().path("id");
        String id = as(admin).body(Map.of("email", "roles-" + n + "@example.bf", "firstName", "A", "lastName", "B"))
                .when().post("/api/users").then().statusCode(201).extract().path("user.id");

        // type modifiable
        as(admin).body(Map.of("email", "roles-" + n + "@example.bf", "firstName", "A", "lastName", "B",
                        "type", "PERSONNEL"))
                .when().patch("/api/users/" + id).then().statusCode(200).body("type", equalTo("PERSONNEL"));

        // plusieurs rôles ; au moins un rôle obligatoire
        as(admin).body(Map.of("roles", List.of("PARTICIPANT", "PERSONNEL_CONTROLE")))
                .when().put("/api/users/" + id + "/roles").then().statusCode(200)
                .body("permissions", hasItem("CHECKIN_SCAN"));
        as(admin).body(Map.of("roles", List.of()))
                .when().put("/api/users/" + id + "/roles").then().statusCode(422).body("code", equalTo("ROLES_REQUIRED"));

        // l'admin ne peut pas se retirer ses propres droits, ni se suspendre
        as(admin).body(Map.of("roles", List.of("PARTICIPANT")))
                .when().put("/api/users/" + adminId + "/roles").then().statusCode(422)
                .body("code", equalTo("SELF_LOCKOUT"));
        as(admin).body(Map.of("status", "DESACTIVE"))
                .when().patch("/api/users/" + adminId + "/status").then().statusCode(422);

        // filtre par rôle
        as(admin).when().get("/api/users?role=PERSONNEL_CONTROLE&size=200").then().statusCode(200)
                .body("content.id", hasItem(id));

        // dernier super-administrateur : un autre gestionnaire ne peut pas le suspendre
        int actifs = as(admin).when().get("/api/users?role=SUPER_ADMIN&status=ACTIF&size=200")
                .then().extract().path("totalElements");
        as(admin).body(Map.of("name", "GESTION_COMPTES_" + (n % 100000), "description", "Gère les comptes",
                        "permissions", List.of("USER_READ", "USER_MANAGE", "ROLE_MANAGE")))
                .when().post("/api/roles").then().statusCode(201);
        String gestPwd = as(admin).body(Map.of("email", "gest-" + n + "@example.bf", "firstName", "G", "lastName", "H",
                        "roles", List.of("GESTION_COMPTES_" + (n % 100000))))
                .when().post("/api/users").then().statusCode(201).extract().path("motDePasseTemporaire");
        String gest = TestAuth.login("gest-" + n + "@example.bf", gestPwd);
        if (actifs == 1) {
            as(gest).body(Map.of("status", "DESACTIVE"))
                    .when().patch("/api/users/" + adminId + "/status").then().statusCode(422)
                    .body("code", equalTo("LAST_SUPER_ADMIN"));
            as(gest).body(Map.of("roles", List.of("PARTICIPANT")))
                    .when().put("/api/users/" + adminId + "/roles").then().statusCode(422)
                    .body("code", equalTo("LAST_SUPER_ADMIN"));
        }
    }

    @Test
    void custom_roles_can_be_edited_and_deleted_only_when_unused() {
        long n = System.nanoTime();
        String admin = TestAuth.adminToken();
        String name = "AGENT_" + (n % 1_000_000);
        String roleId = as(admin).body(Map.of("name", name, "description", "Agent", "permissions", List.of("EVENT_READ")))
                .when().post("/api/roles").then().statusCode(201).extract().path("id");

        as(admin).body(Map.of("description", "Agent d'accueil", "permissions", List.of("EVENT_READ", "CHECKIN_SCAN")))
                .when().put("/api/roles/" + roleId).then().statusCode(200)
                .body("description", equalTo("Agent d'accueil"))
                .body("permissions", equalTo(List.of("CHECKIN_SCAN", "EVENT_READ")));

        String userId = as(admin).body(Map.of("email", "agent-" + n + "@example.bf", "firstName", "A", "lastName", "B",
                        "roles", List.of(name)))
                .when().post("/api/users").then().statusCode(201).extract().path("user.id");
        as(admin).when().delete("/api/roles/" + roleId).then().statusCode(422).body("code", equalTo("ROLE_IN_USE"));

        as(admin).body(Map.of("roles", List.of("PARTICIPANT")))
                .when().put("/api/users/" + userId + "/roles").then().statusCode(200);
        as(admin).when().delete("/api/roles/" + roleId).then().statusCode(204);

        // les rôles système restent verrouillés
        String superId = as(admin).when().get("/api/roles").then().extract()
                .path("find { it.name == 'SUPER_ADMIN' }.id");
        as(admin).body(Map.of("description", "x", "permissions", List.of()))
                .when().put("/api/roles/" + superId).then().statusCode(422)
                .body("code", equalTo("SYSTEM_ROLE_LOCKED"));
    }
}
