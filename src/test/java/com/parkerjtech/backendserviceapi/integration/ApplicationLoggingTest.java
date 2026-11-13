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
    @Autowired com.parkerjtech.backendserviceapi.service.WorkspaceCleanupService cleanup;
    @Autowired LeadRepository leads;
    @Autowired TaskRepository tasks;
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
        assertThat(output.getOut()).contains("localTime=2026-11-11T06:00:00-06:00", "Alex Morgan + "+response.json().get("id")+" has been created as a lead");
        assertThat(output.getOut()).contains("Lead creation input validated", "Lead capacity checked", "priority has been assigned", "follow-up task", "has been created for lead", "activity event", "has been recorded for lead");
        assertThat(output.getOut()).contains("Request completed", "method=POST", "path=/api/demo/leads", "status=201");
    }
    @Test void doesNotReportSuccessWhenOuterTransactionRollsBack(CapturedOutput output) {
        var ws=workspace(workspaces,TestTimeConfiguration.NOW);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.create(ws.id(),new CreateLeadCommand("Rollback Demo","Demo",Lead.ServiceInterest.WEBSITE,Lead.Source.OTHER,BigDecimal.ZERO,null));
            assertThat(output.getOut()).doesNotContain("has been created as a lead", "has been created for lead", "has been recorded for lead");
            status.setRollbackOnly();
        });
        assertThat(output.getOut()).doesNotContain("has been created as a lead", "has been created for lead", "has been recorded for lead");
    }
    @Test void logsFailuresWithStackTraceAndPreservesSafeResponse(CapturedOutput output) throws Exception {
        var client=new HttpTestClient(port);var session=client.get("/api/demo/session").json();
        doThrow(new IllegalStateException("forced logging test failure")).when(activity).insert(any(ActivityEvent.class));
        var response=client.post("/api/demo/leads",payload(),session);
        assertThat(response.status()).isEqualTo(500);
        assertThat(output.getAll()).contains("Unexpected API failure", "IllegalStateException", "forced logging test failure", "status=500");
        assertThat(output.getAll()).doesNotContain("has been created as a lead", "has been created for lead", "has been recorded for lead");
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
        assertThat(output.getOut()).contains("Alex Forged + "+response.json().get("id")+" has been created as a lead").doesNotContain("Alex\nForged +");
    }
    @Test void logsSessionCreationAndRecovery(CapturedOutput output) throws Exception {
        var client=new HttpTestClient(port);
        var created=client.get("/api/demo/session");
        var recovered=client.get("/api/demo/session");
        assertThat(recovered.json().get("workspaceId")).isEqualTo(created.json().get("workspaceId"));
        assertThat(output.getOut()).contains("Demo workspace "+created.json().get("workspaceId")+" has been created for session initialization",
            "Demo session recovered for workspace "+created.json().get("workspaceId"));
    }
    @Test void logsSingleAndListRetrievalWithNamesIdsAndCounts(CapturedOutput output) throws Exception {
        var client=new HttpTestClient(port);var session=client.get("/api/demo/session").json();
        var created=client.post("/api/demo/leads",payload(),session);
        var id=created.json().get("id");
        assertThat(client.get("/api/demo/leads/"+id).status()).isEqualTo(200);
        assertThat(client.get("/api/demo/leads").items()).hasSize(1);
        assertThat(output.getOut()).contains("Alex Morgan + "+id+" has been retrieved as a lead",
            "Retrieved 1 leads from demo workspace "+session.get("workspaceId"),"Demo workspace validated");
    }
    @Test void logsTaskAndActivityRetrievalAndCreationIds(CapturedOutput output) throws Exception {
        var client=new HttpTestClient(port);var session=client.get("/api/demo/session").json();
        var lead=client.post("/api/demo/leads",payload(),session);
        var tasks=client.get("/api/demo/tasks");var events=client.get("/api/demo/activity");
        var taskId=tasks.items().get(0).get("id");
        assertThat(output.getOut()).contains("Initial follow-up task "+taskId+" has been created for lead "+lead.json().get("id"),
            "Follow-up task "+taskId+" has been retrieved for lead "+lead.json().get("id"),"Retrieved 1 tasks", "Retrieved 3 activity events");
        for(var event:events.items()) {
            assertThat(output.getOut()).contains(event.get("type")+" activity event "+event.get("id")+" has been recorded for lead "+lead.json().get("id"),
                "Activity event "+event.get("id")+" has been retrieved for lead "+lead.json().get("id"));
        }
    }
    @Test void logsEmptyListsExplicitly(CapturedOutput output) throws Exception {
        var client=new HttpTestClient(port);client.get("/api/demo/session");
        for(var resource:java.util.List.of("leads","tasks","activity")) assertThat(client.get("/api/demo/"+resource).items()).isEmpty();
        assertThat(output.getOut()).contains("Retrieved 0 leads", "Retrieved 0 tasks", "Retrieved 0 activity events");
    }

    @Test void logsCleanupSubActionsAndDeletedRecordCounts(CapturedOutput output) {
        var ws=workspace(workspaces,TestTimeConfiguration.NOW.minus(java.time.Duration.ofHours(48)));
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            workspaces.lock(ws.id());
            var lead=com.parkerjtech.backendserviceapi.support.DatabaseFixtures.lead(leads,ws,ws.createdAt(),"Expired Demo",new BigDecimal("10.00"));
            tasks.insertInitialIfAbsent(com.parkerjtech.backendserviceapi.support.DatabaseFixtures.task(lead));
            activity.insert(com.parkerjtech.backendserviceapi.support.DatabaseFixtures.event(lead,ActivityEvent.Type.LEAD_CREATED));
        });
        assertThat(cleanup.cleanupExpired(TestTimeConfiguration.NOW,100)).isPositive();
        assertThat(output.getOut()).contains("Expired demo workspace cleanup started", "Cleanup completed",
            "Removed 1 activity events from expired demo workspace "+ws.id(),
            "Removed 1 tasks from expired demo workspace "+ws.id(),
            "Removed 1 leads from expired demo workspace "+ws.id(),
            "Expired demo workspace "+ws.id()+" has been deleted");
    }

}
