package com.parkerjtech.backendserviceapi.integration;

import com.parkerjtech.backendserviceapi.model.*;
import com.parkerjtech.backendserviceapi.repository.*;
import com.parkerjtech.backendserviceapi.exception.ApiException;
import com.parkerjtech.backendserviceapi.service.LeadService;
import com.parkerjtech.backendserviceapi.support.*;
import java.math.BigDecimal;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.parkerjtech.backendserviceapi.support.DatabaseFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test") @Import(TestTimeConfiguration.class)
class LeadMutationServiceTest {
    @Autowired LeadService service;
    @MockitoSpyBean WorkspaceRepository workspaces;
    @Autowired LeadRepository leads;
    @Autowired TaskRepository tasks;
    @MockitoSpyBean ActivityRepository activity;
    @Autowired MutableClock clock;
    @Autowired PlatformTransactionManager transactions;
    @BeforeEach void resetTime() { clock.setInstant(TestTimeConfiguration.NOW); reset(workspaces,activity); }

    private CreateLeadCommand initial() {
        return new CreateLeadCommand("Alex Morgan","Demo Co",Lead.ServiceInterest.WEBSITE,Lead.Source.REFERRAL,
                new BigDecimal("7500.00"),Lead.ContactPreference.EMAIL);
    }

    @Test void editsBusinessFieldsRecalculatesPriorityAndPreservesStageAndTask() {
        var ws=workspace(workspaces,TestTimeConfiguration.NOW);
        var created=service.create(ws.id(),initial());
        var taskBefore=tasks.list(ws.id()).getFirst();
        var changedAt=TestTimeConfiguration.NOW.plusSeconds(60);
        clock.setInstant(changedAt);

        var updated=service.update(ws.id(),created.id(),new CreateLeadCommand(" Alex Morgan ","New Co",
                Lead.ServiceInterest.CUSTOM_APPLICATION,Lead.Source.SOCIAL_MEDIA,new BigDecimal("999.99"),Lead.ContactPreference.PHONE));

        assertThat(updated.name()).isEqualTo("Alex Morgan");
        assertThat(updated.company()).isEqualTo("New Co");
        assertThat(updated.priority()).isEqualTo(Lead.Priority.LOW);
        assertThat(updated.stage()).isEqualTo(Lead.Stage.NEW);
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
        assertThat(updated.updatedAt()).isEqualTo(changedAt);
        assertThat(updated.lastActivityAt()).isEqualTo(changedAt);
        assertThat(leads.find(ws.id(),created.id())).contains(updated);
        assertThat(tasks.list(ws.id())).containsExactly(taskBefore);
        assertThat(activity.list(ws.id())).extracting(ActivityEvent::type).containsExactly(
                ActivityEvent.Type.LEAD_CREATED,ActivityEvent.Type.PRIORITY_ASSIGNED,ActivityEvent.Type.FOLLOW_UP_CREATED,
                ActivityEvent.Type.LEAD_UPDATED,ActivityEvent.Type.PRIORITY_ASSIGNED);
        assertThat(activity.list(ws.id()).get(3).description()).contains("company", "serviceInterest", "source", "estimatedValue", "contactPreference");
    }

    @Test void normalizedIdenticalEditDoesNotChangeTimestampsOrAddEvents() {
        var ws=workspace(workspaces,TestTimeConfiguration.NOW);
        var created=service.create(ws.id(),initial());
        clock.setInstant(TestTimeConfiguration.NOW.plusSeconds(300));

        var unchanged=service.update(ws.id(),created.id(),new CreateLeadCommand(" Alex Morgan "," Demo Co ",
                Lead.ServiceInterest.WEBSITE,Lead.Source.REFERRAL,new BigDecimal("7500.00"),Lead.ContactPreference.EMAIL));

        assertThat(unchanged).isEqualTo(created);
        assertThat(activity.list(ws.id())).hasSize(3);
    }

    @Test void changesAnyStageAndSameStageIsANoop() {
        var ws=workspace(workspaces,TestTimeConfiguration.NOW);
        var created=service.create(ws.id(),initial());
        clock.setInstant(TestTimeConfiguration.NOW.plusSeconds(60));

        var changed=service.changeStage(ws.id(),created.id(),Lead.Stage.WON);
        var unchanged=service.changeStage(ws.id(),created.id(),Lead.Stage.WON);

        assertThat(changed.stage()).isEqualTo(Lead.Stage.WON);
        assertThat(unchanged).isEqualTo(changed);
        assertThat(unchanged.updatedAt()).isEqualTo(TestTimeConfiguration.NOW.plusSeconds(60));
        assertThat(activity.list(ws.id())).extracting(ActivityEvent::type).containsExactly(
                ActivityEvent.Type.LEAD_CREATED,ActivityEvent.Type.PRIORITY_ASSIGNED,ActivityEvent.Type.FOLLOW_UP_CREATED,
                ActivityEvent.Type.LEAD_STAGE_CHANGED);
        assertThat(activity.list(ws.id()).getLast().description()).contains("NEW", "WON");
        assertThat(tasks.list(ws.id())).singleElement().satisfies(task -> assertThat(task.status()).isEqualTo(CrmTask.Status.OPEN));
    }

    @Test void allowsAllStagesAndRecalculatesPriorityAtBothBoundaries() {
        var ws=workspace(workspaces,TestTimeConfiguration.NOW);
        var created=service.create(ws.id(),initial());
        for(var stage:Lead.Stage.values()) created=service.changeStage(ws.id(),created.id(),stage);
        var medium=service.update(ws.id(),created.id(),new CreateLeadCommand(created.name(),created.company(),created.serviceInterest(),
                created.source(),new BigDecimal("1000.00"),created.contactPreference()));
        var high=service.update(ws.id(),created.id(),new CreateLeadCommand(created.name(),created.company(),created.serviceInterest(),
                created.source(),new BigDecimal("5000.00"),created.contactPreference()));
        assertThat(created.stage()).isEqualTo(Lead.Stage.LOST);
        assertThat(medium.priority()).isEqualTo(Lead.Priority.MEDIUM);
        assertThat(high.priority()).isEqualTo(Lead.Priority.HIGH);
    }

    @Test void activityFailureRollsBackLeadEdit() {
        var ws=workspace(workspaces,TestTimeConfiguration.NOW);
        var created=service.create(ws.id(),initial());
        clock.setInstant(TestTimeConfiguration.NOW.plusSeconds(30));
        doThrow(new IllegalStateException("forced update event failure")).when(activity)
                .insert(argThat(event -> event.type()==ActivityEvent.Type.LEAD_UPDATED));

        assertThatThrownBy(() -> service.update(ws.id(),created.id(),new CreateLeadCommand("New Name","New Co",
                Lead.ServiceInterest.CUSTOM_APPLICATION,Lead.Source.SOCIAL_MEDIA,new BigDecimal("1000.00"),null)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(leads.find(ws.id(),created.id())).contains(created);
        assertThat(activity.list(ws.id())).hasSize(3);
    }

    @Test void rechecksWorkspaceExpiryAfterWaitingForItsLock() throws Exception {
        var ws=workspace(workspaces,TestTimeConfiguration.NOW);
        var created=service.create(ws.id(),initial());
        var attempted=new CountDownLatch(1);
        var result=new java.util.concurrent.atomic.AtomicReference<Future<String>>();
        try(var executor=Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                workspaces.lock(ws.id());
                doAnswer(invocation -> { attempted.countDown(); return invocation.callRealMethod(); }).when(workspaces).lock(ws.id());
                result.set(executor.submit(() -> {
                    try { service.changeStage(ws.id(),created.id(),Lead.Stage.CONTACTED); return "CHANGED"; }
                    catch(ApiException ex) { return ex.code(); }
                }));
                try { assertThat(attempted.await(10,TimeUnit.SECONDS)).isTrue(); }
                catch(InterruptedException ex) { throw new RuntimeException(ex); }
                clock.setInstant(ws.expiresAt());
            });
            assertThat(result.get().get(20,TimeUnit.SECONDS)).isEqualTo("WORKSPACE_REQUIRED");
        }
        assertThat(leads.find(ws.id(),created.id())).contains(created);
        assertThat(activity.list(ws.id())).hasSize(3);
    }
}
