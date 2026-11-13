package com.parkerjtech.backendserviceapi.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import com.parkerjtech.backendserviceapi.support.HttpTestClient;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"local","test"})
class LocalH2ConsoleTest {
    @LocalServerPort int port;
    private HttpResponse<String> console(String method,String path) throws Exception {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build().send(
            HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).method(method,HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString());
    }
    @Test void servesConsoleWithSameOriginFrames() throws Exception {
        var response=console("GET","/h2-console/");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).containsIgnoringCase("H2 Console");
        assertThat(response.headers().firstValue("X-Frame-Options")).contains("SAMEORIGIN");
    }
    @Test void consolePostIsExemptButApiStillRequiresCsrf() throws Exception {
        var response=console("POST","/h2-console/login.do");
        assertThat(response.statusCode()).isIn(200,302);
        assertThat(response.body()).doesNotContain("CSRF_INVALID");
        var api=new HttpTestClient(port);
        var bootstrap=api.get("/api/demo/session");
        assertThat(bootstrap.status()).isEqualTo(200);
        assertThat(bootstrap.headers().firstValue("X-Frame-Options")).contains("DENY");
        assertThat(api.send("POST","/api/demo/leads",Map.of("name","Demo"),Map.of()).status()).isEqualTo(403);
    }
}
