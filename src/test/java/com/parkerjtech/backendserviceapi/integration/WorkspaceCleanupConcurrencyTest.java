package com.parkerjtech.backendserviceapi.integration;
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
import com.parkerjtech.backendserviceapi.model.*;
import com.parkerjtech.backendserviceapi.repository.*;
import com.parkerjtech.backendserviceapi.service.*;
import com.parkerjtech.backendserviceapi.exception.ApiException;
import com.parkerjtech.backendserviceapi.support.*;
import static com.parkerjtech.backendserviceapi.support.DatabaseFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test") @Import(TestTimeConfiguration.class)
class WorkspaceCleanupConcurrencyTest {
    @Autowired WorkspaceCleanupTransaction cleanup;
    @Autowired LeadService service;
    @MockitoSpyBean WorkspaceRepository workspaces;
    @Autowired LeadRepository leads;
    @Autowired TaskRepository tasks;
    @Autowired ActivityRepository activity;
    @Autowired MutableClock clock;
    @Autowired PlatformTransactionManager transactions;
    @BeforeEach void resetTime() { clock.setInstant(TestTimeConfiguration.NOW); }
    private CreateLeadCommand command() {
        return new CreateLeadCommand("Fictional Sam","Demo Co",Lead.ServiceInterest.WEBSITE,Lead.Source.OTHER,BigDecimal.ZERO,null);
    }
    @Test void cleanupWaitsAndPreservesActiveBundle() throws Exception { assertBlockedCleanup(false); }
    @Test void cleanupWaitsAndRemovesWholeExpiredBundle() throws Exception { assertBlockedCleanup(true); }
    private void assertBlockedCleanup(boolean expire) throws Exception {
        var ws=workspace(workspaces,TestTimeConfiguration.NOW);var attempted=new CountDownLatch(1);
        var result=new AtomicReference<Future<Boolean>>();
        try(var executor=Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                workspaces.lock(ws.id());
                service.create(ws.id(),command());
                if(expire)clock.setInstant(ws.expiresAt());
                doAnswer(invocation -> { attempted.countDown();return invocation.callRealMethod(); }).when(workspaces).lock(ws.id());
                result.set(executor.submit(() -> cleanup.deleteIfExpired(ws.id(),clock.instant())));
                try { assertThat(attempted.await(10,TimeUnit.SECONDS)).isTrue(); }
                catch(InterruptedException ex) { throw new RuntimeException(ex); }
            });
            assertThat(result.get().get(20,TimeUnit.SECONDS)).isEqualTo(expire);
        }
        assertThat(leads.count(ws.id())).isEqualTo(expire?0:1);
        assertThat(tasks.list(ws.id())).hasSize(expire?0:1);
        assertThat(activity.list(ws.id())).hasSize(expire?0:3);
    }
    @Test void queuedCreateFailsAfterCleanupWinsLock() throws Exception {
        var ws=workspace(workspaces,TestTimeConfiguration.NOW);service.create(ws.id(),command());clock.setInstant(ws.expiresAt());
        var attempted=new CountDownLatch(1);var result=new AtomicReference<Future<String>>();
        try(var executor=Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                assertThat(cleanup.deleteIfExpired(ws.id(),clock.instant())).isTrue();
                doAnswer(invocation -> { attempted.countDown();return invocation.callRealMethod(); }).when(workspaces).lock(ws.id());
                result.set(executor.submit(() -> {
                    try { service.create(ws.id(),command());return "CREATED"; }
                    catch(ApiException ex) { return ex.code(); }
                }));
                try { assertThat(attempted.await(10,TimeUnit.SECONDS)).isTrue(); }
                catch(InterruptedException ex) { throw new RuntimeException(ex); }
            });
            assertThat(result.get().get(20,TimeUnit.SECONDS)).isEqualTo("WORKSPACE_REQUIRED");
        }
        assertThat(workspaces.find(ws.id())).isEmpty();
        assertThat(leads.count(ws.id())).isZero();assertThat(tasks.list(ws.id())).isEmpty();assertThat(activity.list(ws.id())).isEmpty();
    }
}
