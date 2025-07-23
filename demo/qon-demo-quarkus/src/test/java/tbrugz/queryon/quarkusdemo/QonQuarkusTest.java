package tbrugz.queryon.quarkusdemo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class QonQuarkusTest {

    public QonQuarkusTest() {
        //System.out.println("QonQuarkusTest()");
    }

    @Test
    public void testRelationEndpoint() {
        //System.out.println("testRelationEndpoint...");
        given()
          .when().get("/q/relation.json")
          .then()
             .statusCode(200)
             //.body(is("hello2"));
             ;
    }

    /*
    @Test
    public void testGreetingEndpoint() {
        String uuid = UUID.randomUUID().toString();
        given()
          .pathParam("name", uuid)
          .when().get("/hello/greeting/{name}")
          .then()
            .statusCode(200)
            .body(is("hello-zz " + uuid));
    }
    */

}
