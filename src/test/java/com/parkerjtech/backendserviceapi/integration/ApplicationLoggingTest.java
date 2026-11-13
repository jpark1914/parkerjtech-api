package com.parkerjtech.backendserviceapi.integration;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.parkerjtech.backendserviceapi.model.*;
import com.parkerjtech.backendserviceapi.repository.*;
import com.parkerjtech.backendserviceapi.service.LeadService;
import com.parkerjtech.backendserviceapi.support.*;
import static com.parkerjtech.backendserviceapi.support.DatabaseFixtures.workspace;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "logging.level.com.parkerjtech.backendserviceapi=INFO",
    "crm.logging.zone=America/Chicago", "crm.logging.exception-stack-traces=true"})
@ActiveProfiles("test") @Import(TestTimeConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ApplicationLoggingTest {
    @LocalServerPort int port;
    @Autowired MutableClock clock;
    @Autowired LeadService service;
    @Autowired WorkspaceRepository workspaces;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean ActivityRepository activity;
    @BeforeEach void resetTime() { clock.setInstant(TestTimeConfiguration.NOW); }
    private Map<String,Object> payload() {
        return Map.of("name","Alex Morgan","company","Demo","serviceInterest","WEBSITE","source","OTHER","estimatedValue",7500);
    }
    @Test void logsCommittedLeadWithLocalTimeNameAndId(CapturedOutput output) throws Exception {
        var client=new HttpTestClient(port);var session=client.get("/api/demo/session").json();
        var response=client.post("/api/demo/leads",payload(),session);
        assertThat(response.status()).isEqualTo(201);
        assertThat(output.getOut()).contains("Lead created", "localTime=2026-11-11T06:00:00-06:00", "lead=Alex Morgan?"+response.json().get("id"));
        assertThat(output.getOut()).contains("Request completed", "method=POST", "path=/api/demo/leads", "status=201");
    }
    @Test void doesNotReportSuccessWhenOuterTransactionRollsBack(CapturedOutput output) {
        var ws=workspace(workspaces,TestTimeConfiguration.NOW);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.create(ws.id(),new CreateLeadCommand("Rollback Demo","Demo",Lead.ServiceInterest.WEBSITE,Lead.Source.OTHER,BigDecimal.ZERO,null));
            assertThat(output.getOut()).doesNotContain("lead=Rollback Demo?");
            status.setRollbackOnly();
        });
        assertThat(output.getOut()).doesNotContain("lead=Rollback Demo?");
    }
    @Test void logsFailuresWithStackTraceAndPreservesSafeResponse(CapturedOutput output) throws Exception {
        var client=new HttpTestClient(port);var session=client.get("/api/demo/session").json();
        doThrow(new IllegalStateException("forced logging test failure")).when(activity).insert(any(ActivityEvent.class));
        var response=client.post("/api/demo/leads",payload(),session);
        assertThat(response.status()).isEqualTo(500);
        assertThat(output.getAll()).contains("Unexpected API failure", "IllegalStateException", "forced logging test failure", "status=500");
        assertThat(output.getAll()).doesNotContain("Lead created");
        assertThat(response.body()).doesNotContain("forced logging test failure", "IllegalStateException");
    }
    @Test void logsRejectedRequestsWithoutCookieOrCsrfValues(CapturedOutput output) throws Exception {
        var client=new HttpTestClient(port);
        assertThat(client.get("/api/demo/leads").status()).isEqualTo(401);
        assertThat(client.send("POST","/api/demo/leads",payload(),Map.of("X-CSRF-TOKEN","sensitive-token-for-test")).status()).isEqualTo(403);
        assertThat(output.getAll()).contains("WORKSPACE_REQUIRED", "CSRF_INVALID", "status=401", "status=403");
        assertThat(output.getAll()).doesNotContain("sensitive-token-for-test");
    }
    @Test void makesLeadNamesSingleLineInLogs(CapturedOutput output) throws Exception {
        var client=new HttpTestClient(port);var session=client.get("/api/demo/session").json();
        var body=new java.util.HashMap<String,Object>(payload());body.put("name","Alex\nForged");
        var response=client.post("/api/demo/leads",body,session);
        assertThat(response.status()).isEqualTo(201);
        assertThat(output.getOut()).contains("lead=Alex Forged?").doesNotContain("lead=Alex\nForged?");
    }
}
