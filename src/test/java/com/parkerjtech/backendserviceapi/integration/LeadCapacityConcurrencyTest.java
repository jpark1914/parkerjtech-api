package com.parkerjtech.backendserviceapi.integration;
import java.math.BigDecimal;
import java.util.concurrent.*;
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

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test") @Import(TestTimeConfiguration.class)
class LeadCapacityConcurrencyTest {
    @Autowired LeadService service;
    @MockitoSpyBean WorkspaceRepository workspaces;
    @Autowired LeadRepository leads;
    @Autowired TaskRepository tasks;
    @Autowired ActivityRepository activity;
    @Autowired MutableClock clock;
    @Autowired PlatformTransactionManager transactions;
    @BeforeEach void resetTime() { clock.setInstant(TestTimeConfiguration.NOW); }
    private CreateLeadCommand command() {
        return new CreateLeadCommand("Fictional Sam","Demo",Lead.ServiceInterest.WEBSITE,Lead.Source.OTHER,new BigDecimal("1000"),null);
    }
    @Test void serializesTwoCreatesAtTheCapacityBoundary() throws Exception {
        var ws=workspace(workspaces,TestTimeConfiguration.NOW);
        for(int i=0;i<99;i++) service.create(ws.id(),command());
        var barrier=new CyclicBarrier(2);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<String> create=() -> {
                barrier.await(10,TimeUnit.SECONDS);
                try { service.create(ws.id(),command()); return "CREATED"; }
                catch(ApiException ex) { return ex.code(); }
            };
            var a=executor.submit(create);var b=executor.submit(create);
            assertThat(java.util.List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("CREATED","WORKSPACE_LIMIT_REACHED");
        }
        assertThat(leads.count(ws.id())).isEqualTo(100);
        assertThat(tasks.list(ws.id())).hasSize(100);
        assertThat(activity.list(ws.id())).hasSize(300);
    }
    @Test void rechecksExpiryAfterWaitingForWorkspaceLock() throws Exception {
        var ws=workspace(workspaces,TestTimeConfiguration.NOW);
        var attempted=new CountDownLatch(1);
        var result=new java.util.concurrent.atomic.AtomicReference<Future<String>>();
        try(var executor=Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                workspaces.lock(ws.id());
                doAnswer(invocation -> { attempted.countDown(); return invocation.callRealMethod(); }).when(workspaces).lock(ws.id());
                result.set(executor.submit(() -> {
                    try { service.create(ws.id(),command()); return "CREATED"; }
                    catch(ApiException ex) { return ex.code(); }
                }));
                try { assertThat(attempted.await(10,TimeUnit.SECONDS)).isTrue(); }
                catch(InterruptedException ex) { throw new RuntimeException(ex); }
                clock.setInstant(ws.expiresAt());
            });
            assertThat(result.get().get(20,TimeUnit.SECONDS)).isEqualTo("WORKSPACE_REQUIRED");
        }
        assertThat(leads.count(ws.id())).isZero();
    }
}
