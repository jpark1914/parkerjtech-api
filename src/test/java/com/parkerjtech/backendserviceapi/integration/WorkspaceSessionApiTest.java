package com.parkerjtech.backendserviceapi.integration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.parkerjtech.backendserviceapi.support.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestTimeConfiguration.class)
class WorkspaceSessionApiTest {
    @LocalServerPort int port;
    @Autowired MutableClock clock;
    @BeforeEach void resetTime() { clock.setInstant(TestTimeConfiguration.NOW); }
    @Test void reusesWorkspaceForSameCookie() throws Exception {
        var client=new HttpTestClient(port); var first=client.get("/api/demo/session");
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.json().get("createdAt")).isEqualTo("2026-11-11T12:00:00Z");
        assertThat(first.json().get("expiresAt")).isEqualTo("2026-11-12T12:00:00Z");
        assertThat(first.json().get("csrfToken")).isInstanceOf(String.class).isNotEqualTo("");
        assertThat(first.json().get("csrfHeaderName")).isEqualTo("X-CSRF-TOKEN");
        assertThat(first.headers().allValues("set-cookie")).anySatisfy(cookie -> {
            assertThat(cookie).contains("HttpOnly","SameSite=Lax","Max-Age=86400").doesNotContain("Secure");
        });
        var second=client.get("/api/demo/session");
        assertThat(second.json().get("workspaceId")).isEqualTo(first.json().get("workspaceId"));
        assertThat(first.headers().firstValue("cache-control").orElse("")).contains("no-store");
    }
    @Test void isolatesSeparateCookieJars() throws Exception {
        var first=new HttpTestClient(port).get("/api/demo/session");
        var second=new HttpTestClient(port).get("/api/demo/session");
        assertThat(first.status()).isEqualTo(200);
        assertThat(second.json().get("workspaceId")).isNotEqualTo(first.json().get("workspaceId"));
    }
    @Test void expiresAtExactDeadline() throws Exception {
        var client=new HttpTestClient(port); var first=client.get("/api/demo/session");
        assertThat(first.status()).isEqualTo(200);
        clock.setInstant(Instant.parse((String)first.json().get("expiresAt")));
        assertThat(client.get("/api/demo/session").json().get("workspaceId")).isNotEqualTo(first.json().get("workspaceId"));
    }
    @Test void fabricatedCookieCannotRecoverAnotherWorkspace() throws Exception {
        var first=new HttpTestClient(port).get("/api/demo/session");
        var second=new HttpTestClient(port).send("GET","/api/demo/session",null,java.util.Map.of("Cookie","SESSION=fabricated-cookie"));
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.json().get("workspaceId")).isNotEqualTo(first.json().get("workspaceId"));
    }
}
