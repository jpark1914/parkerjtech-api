package com.parkerjtech.backendserviceapi.integration;

import java.net.CookieManager;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import com.parkerjtech.backendserviceapi.BackendserviceapiApplication;
import com.parkerjtech.backendserviceapi.support.*;
import static org.assertj.core.api.Assertions.*;

class SessionRestartPersistenceTest {
    @TempDir Path directory;
    private ConfigurableApplicationContext start(String url) {
        return new SpringApplicationBuilder(BackendserviceapiApplication.class,TestTimeConfiguration.class)
            .profiles("test").run("--server.port=0","--spring.datasource.url="+url);
    }
    private int port(ConfigurableApplicationContext context) {
        return context.getEnvironment().getRequiredProperty("local.server.port",Integer.class);
    }
    @Test void preservesWorkspaceAndLeadAcrossRestart() throws Exception {
        var url="jdbc:h2:file:"+directory.resolve("restart-crm")+";DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000";
        CookieManager cookies;
        Map<String,Object> originalSession;
        String leadId;
        var payload=Map.of("name","Restart Demo","company","Fictional Co","serviceInterest","WEBSITE","source","OTHER","estimatedValue",5000);
        try(var first=start(url)) {
            var client=new HttpTestClient(port(first));
            var bootstrap=client.get("/api/demo/session");assertThat(bootstrap.status()).isEqualTo(200);
            originalSession=bootstrap.json();cookies=client.cookies();
            var created=client.post("/api/demo/leads",payload,originalSession);
            assertThat(created.status()).isEqualTo(201);leadId=(String)created.json().get("id");
        }
        try(var second=start(url)) {
            var client=new HttpTestClient(port(second),cookies);
            var bootstrap=client.get("/api/demo/session");assertThat(bootstrap.status()).isEqualTo(200);
            assertThat(bootstrap.json().get("workspaceId")).isEqualTo(originalSession.get("workspaceId"));
            var fetched=client.get("/api/demo/leads/"+leadId);
            assertThat(fetched.status()).isEqualTo(200);assertThat(fetched.json()).containsEntry("id",leadId);
            assertThat(client.get("/api/demo/tasks").items()).singleElement().satisfies(task -> assertThat(task).containsEntry("leadId",leadId));
            assertThat(client.get("/api/demo/activity").items()).hasSize(3).allSatisfy(event -> assertThat(event).containsEntry("leadId",leadId));
            assertThat(client.post("/api/demo/leads",payload,bootstrap.json()).status()).isEqualTo(201);
            assertThat(client.get("/api/demo/leads").items()).hasSize(2);
        }
    }
}
