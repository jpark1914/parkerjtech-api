package com.parkerjtech.backendserviceapi.integration;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.parkerjtech.backendserviceapi.model.*;
import com.parkerjtech.backendserviceapi.service.*;
import com.parkerjtech.backendserviceapi.repository.*;
import com.parkerjtech.backendserviceapi.exception.ApiException;
import com.parkerjtech.backendserviceapi.support.*;
import static com.parkerjtech.backendserviceapi.support.DatabaseFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test") @Import(TestTimeConfiguration.class)
class LeadCreationServiceTest {
    @Autowired LeadService service;
    @Autowired WorkspaceRepository workspaces;
    @Autowired LeadRepository leads;
    @Autowired TaskRepository tasks;
    @MockitoSpyBean ActivityRepository activity;
    @Autowired MutableClock clock;
    @Autowired PlatformTransactionManager transactions;
    @BeforeEach void resetTime() { clock.setInstant(TestTimeConfiguration.NOW); }
    static CreateLeadCommand command() {
        return new CreateLeadCommand("  Fictional Sam  "," Demo Company ",Lead.ServiceInterest.WEBSITE,Lead.Source.REFERRAL,new BigDecimal("5000"),null);
    }
    @Test void createsOneCompleteAutomationBundle() {
        var ws=workspace(workspaces,TestTimeConfiguration.NOW);
        var lead=service.create(ws.id(),command());
        assertThat(lead.name()).isEqualTo("Fictional Sam");
        assertThat(lead.company()).isEqualTo("Demo Company");
        assertThat(lead.stage()).isEqualTo(Lead.Stage.NEW);
        assertThat(lead.priority()).isEqualTo(Lead.Priority.HIGH);
        assertThat(leads.find(ws.id(),lead.id()).orElseThrow()).isEqualTo(lead);
        assertThat(tasks.list(ws.id())).singleElement().satisfies(task -> {
            assertThat(task.dueAt()).isEqualTo(Instant.parse("2026-11-12T12:00:00Z"));
            assertThat(task.status()).isEqualTo(CrmTask.Status.OPEN);
            assertThat(task.automationRuleKey()).isEqualTo("INITIAL_FOLLOW_UP");
        });
        assertThat(activity.list(ws.id())).extracting(ActivityEvent::type).containsExactly(ActivityEvent.Type.LEAD_CREATED,ActivityEvent.Type.PRIORITY_ASSIGNED,ActivityEvent.Type.FOLLOW_UP_CREATED);
        assertThat(activity.list(ws.id())).allSatisfy(event -> assertThat(event.createdAt()).isEqualTo(Instant.parse("2026-11-11T12:00:00Z")));
    }
    @Test void rollsBackLeadAndTaskWhenActivityFails() {
        var ws=workspace(workspaces,TestTimeConfiguration.NOW);
        doThrow(new IllegalStateException("forced test failure")).when(activity).insert(any(ActivityEvent.class));
        assertThatThrownBy(() -> service.create(ws.id(),command())).isInstanceOf(IllegalStateException.class);
        assertThat(leads.count(ws.id())).isZero();
        assertThat(tasks.list(ws.id())).isEmpty();
        assertThat(activity.list(ws.id())).isEmpty();
    }
    @Test void repeatedRuleDoesNotDuplicateTaskButNewRequestCreatesNewLead() {
        var ws=workspace(workspaces,TestTimeConfiguration.NOW);
        var first=service.create(ws.id(),command());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            workspaces.lock(ws.id());
            assertThat(tasks.insertInitialIfAbsent(task(first))).isFalse();
        });
        assertThat(tasks.list(ws.id())).hasSize(1);
        var second=service.create(ws.id(),command());
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(tasks.list(ws.id())).hasSize(2);
    }
    @Test void deniesExactlyExpiredWorkspace() {
        var ws=workspace(workspaces,TestTimeConfiguration.NOW);
        clock.setInstant(ws.expiresAt());
        assertThatThrownBy(() -> service.create(ws.id(),command())).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.status()).isEqualTo(401));
        assertThat(leads.count(ws.id())).isZero();
    }
    @Test void validatesDirectServiceInputs() {
        var ws=workspace(workspaces,TestTimeConfiguration.NOW);
        var invalid=new CreateLeadCommand(" ","Demo",Lead.ServiceInterest.WEBSITE,Lead.Source.OTHER,new BigDecimal("1.001"),null);
        assertThatThrownBy(() -> service.create(ws.id(),invalid)).isInstanceOf(jakarta.validation.ConstraintViolationException.class);
        assertThat(leads.count(ws.id())).isZero();
    }
}
