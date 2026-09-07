package bf.evenements.plateforme.bootstrap;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

import bf.evenements.plateforme.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.bootstrap.demo-data=true")
class DemoDataIT extends AbstractIntegrationTest {

    @Test
    void demo_events_are_published_and_visible() {
        given().when().get("/api/public/events?search=Semaine")
                .then().statusCode(200)
                .body("totalElements", greaterThanOrEqualTo(1))
                .body("content.find { it.nom.startsWith('Semaine du Num') }.slug", equalTo("semaine-du-numerique-2027"));

        given().when().get("/api/public/events/semaine-du-numerique-2027")
                .then().statusCode(200)
                .body("hasActivities", equalTo(true))
                .body("programme", hasSize(4));

        given().when().get("/api/public/events/siao-2027/stand-types")
                .then().statusCode(200).body("$", hasSize(3));
    }
}
