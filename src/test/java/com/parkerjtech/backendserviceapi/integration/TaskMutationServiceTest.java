package com.parkerjtech.backendserviceapi.integration;

import com.parkerjtech.backendserviceapi.model.*;
import com.parkerjtech.backendserviceapi.repository.*;
import com.parkerjtech.backendserviceapi.service.*;
import com.parkerjtech.backendserviceapi.exception.ApiException;
import com.parkerjtech.backendserviceapi.support.*;
import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test") @Import(TestTimeConfiguration.class)
class TaskMutationServiceTest {
    @Autowired TaskService service;
    @Autowired LeadService leadsService;
    @MockitoSpyBean WorkspaceRepository workspaces;
    @Autowired LeadRepository leads;
    @Autowired TaskRepository tasks;
    @MockitoSpyBean ActivityRepository activity;
    @Autowired MutableClock clock;
    @Autowired PlatformTransactionManager transactions;
    @BeforeEach void resetTime() { clock.setInstant(TestTimeConfiguration.NOW); reset(activity,workspaces); }

    private Lead setup() {
        var ws=workspace(workspaces,TestTimeConfiguration.NOW);
        return leadsService.create(ws.id(),new CreateLeadCommand("Alex Morgan","Demo Co",Lead.ServiceInterest.WEBSITE,
                Lead.Source.REFERRAL,new BigDecimal("5000"),null));
    }

    @Test void completesOnceAndPreservesOriginalCompletionTime() {
        var lead=setup();
        var task=tasks.list(lead.workspaceId()).getFirst();
        var completedAt=TestTimeConfiguration.NOW.plusSeconds(3600);
        clock.setInstant(completedAt);

        var completed=service.complete(lead.workspaceId(),task.id());
        clock.setInstant(completedAt.plusSeconds(600));
        var repeated=service.complete(lead.workspaceId(),task.id());

        assertThat(completed.status()).isEqualTo(CrmTask.Status.COMPLETED);
        assertThat(completed.completedAt()).isEqualTo(completedAt);
        assertThat(repeated).isEqualTo(completed);
        assertThat(tasks.list(lead.workspaceId())).containsExactly(completed);
        assertThat(leads.find(lead.workspaceId(),lead.id()).orElseThrow().updatedAt()).isEqualTo(completedAt);
        assertThat(activity.list(lead.workspaceId())).extracting(ActivityEvent::type)
                .containsExactly(ActivityEvent.Type.LEAD_CREATED,ActivityEvent.Type.PRIORITY_ASSIGNED,
                        ActivityEvent.Type.FOLLOW_UP_CREATED,ActivityEvent.Type.FOLLOW_UP_COMPLETED);
        assertThat(activity.list(lead.workspaceId()).getLast().leadId()).isEqualTo(lead.id());
    }

    @Test void concurrentCompletionWritesOneEventAndOneCompletionTime() throws Exception {
        var lead=setup();
        var task=tasks.list(lead.workspaceId()).getFirst();
        var barrier=new CyclicBarrier(2);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(() -> { barrier.await(10,TimeUnit.SECONDS); return service.complete(lead.workspaceId(),task.id()); });
            var b=executor.submit(() -> { barrier.await(10,TimeUnit.SECONDS); return service.complete(lead.workspaceId(),task.id()); });
            assertThat(a.get(20,TimeUnit.SECONDS).completedAt()).isEqualTo(b.get(20,TimeUnit.SECONDS).completedAt());
        }
        assertThat(activity.list(lead.workspaceId())).filteredOn(event -> event.type()==ActivityEvent.Type.FOLLOW_UP_COMPLETED).hasSize(1);
    }

    @Test void activityFailureRollsBackCompletionAndLeadTimestamps() {
        var lead=setup();
        var task=tasks.list(lead.workspaceId()).getFirst();
        clock.setInstant(TestTimeConfiguration.NOW.plusSeconds(100));
        doThrow(new IllegalStateException("forced completion failure")).when(activity).insert(argThat(event -> event.type()==ActivityEvent.Type.FOLLOW_UP_COMPLETED));

        assertThatThrownBy(() -> service.complete(lead.workspaceId(),task.id())).isInstanceOf(IllegalStateException.class);

        assertThat(tasks.find(lead.workspaceId(),task.id()).orElseThrow()).isEqualTo(task);
        assertThat(leads.find(lead.workspaceId(),lead.id()).orElseThrow()).isEqualTo(lead);
        assertThat(activity.list(lead.workspaceId())).hasSize(3);
    }

    @Test void deniesCompletionAtWorkspaceExpiry() {
        var lead=setup();
        var task=tasks.list(lead.workspaceId()).getFirst();
        var workspace=workspaces.find(lead.workspaceId()).orElseThrow();
        clock.setInstant(workspace.expiresAt());

        assertThatThrownBy(() -> service.complete(lead.workspaceId(),task.id()))
                .isInstanceOfSatisfying(ApiException.class,ex -> assertThat(ex.status()).isEqualTo(401));

        assertThat(tasks.find(lead.workspaceId(),task.id())).contains(task);
        assertThat(activity.list(lead.workspaceId())).hasSize(3);
    }

    @Test void rechecksWorkspaceExpiryAfterWaitingForItsLock() throws Exception {
        var lead=setup();
        var task=tasks.list(lead.workspaceId()).getFirst();
        var workspace=workspaces.find(lead.workspaceId()).orElseThrow();
        var attempted=new CountDownLatch(1);
        var result=new AtomicReference<Future<String>>();
        try(var executor=Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                workspaces.lock(lead.workspaceId());
                doAnswer(invocation -> { attempted.countDown(); return invocation.callRealMethod(); })
                        .when(workspaces).lock(lead.workspaceId());
                result.set(executor.submit(() -> {
                    try { service.complete(lead.workspaceId(),task.id()); return "COMPLETED"; }
                    catch(ApiException ex) { return ex.code(); }
                }));
                try { assertThat(attempted.await(10,TimeUnit.SECONDS)).isTrue(); }
                catch(InterruptedException ex) { throw new RuntimeException(ex); }
                clock.setInstant(workspace.expiresAt());
            });
            assertThat(result.get().get(20,TimeUnit.SECONDS)).isEqualTo("WORKSPACE_REQUIRED");
        }
        assertThat(tasks.find(lead.workspaceId(),task.id())).contains(task);
        assertThat(activity.list(lead.workspaceId())).hasSize(3);
    }
}
