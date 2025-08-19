package tbrugz.queryon.springboot.demo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/*
 * see:
 * https://spring.io/guides/gs/testing-web
 * https://www.baeldung.com/rest-template
 * https://docs.spring.io/spring-framework/reference/testing/annotations/integration-spring/annotation-testpropertysource.html
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource("/test.properties")
public class QonAppTest {

	@LocalServerPort
	int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	public void contextLoads() {
		System.out.println("contextLoads...");
	}

	@Test
	public void getRelations() throws Exception {
		String url = "http://localhost:" + port + "/" + "/q/relation.json";
		ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
		
		Assertions.assertEquals(response.getStatusCode(), HttpStatus.OK);
	}

}
