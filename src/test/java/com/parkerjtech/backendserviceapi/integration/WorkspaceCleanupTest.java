package com.parkerjtech.backendserviceapi.integration;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.parkerjtech.backendserviceapi.model.*;
import com.parkerjtech.backendserviceapi.repository.*;
import com.parkerjtech.backendserviceapi.service.*;
import com.parkerjtech.backendserviceapi.support.*;
import static com.parkerjtech.backendserviceapi.support.DatabaseFixtures.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test") @Import(TestTimeConfiguration.class)
class WorkspaceCleanupTest {
    @Autowired WorkspaceCleanupService cleanup;
    @Autowired WorkspaceCleanupTransaction deletion;
    @Autowired WorkspaceRepository workspaces;
    @Autowired LeadRepository leads;
    @Autowired TaskRepository tasks;
    @Autowired ActivityRepository activity;
    @Autowired PlatformTransactionManager transactions;
    @Test void deletesOnlyExpiredWorkspaceBundles() {
        var now=TestTimeConfiguration.NOW;
        var old=workspace(workspaces,now.minus(Duration.ofHours(48)));
        var exact=workspace(workspaces,now.minus(Duration.ofHours(24)));
        var active=workspace(workspaces,now);
        for(var ws:new DemoWorkspace[]{old,exact,active}) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                workspaces.lock(ws.id());
                var lead=lead(leads,ws,ws.createdAt(),"Fictional Sam",new BigDecimal("100.00"));
                tasks.insertInitialIfAbsent(task(lead));
                activity.insert(event(lead,ActivityEvent.Type.LEAD_CREATED));
            });
        }
        assertThat(cleanup.cleanupExpired(now,100)).isEqualTo(2);
        assertThat(cleanup.cleanupExpired(now,100)).isZero();
        for(var ws:new DemoWorkspace[]{old,exact}) {
            assertThat(workspaces.find(ws.id())).isEmpty();
            assertThat(leads.count(ws.id())).isZero();
            assertThat(tasks.list(ws.id())).isEmpty();
            assertThat(activity.list(ws.id())).isEmpty();
            assertThat(deletion.deleteIfExpired(ws.id(),now)).isFalse();
        }
        assertThat(workspaces.find(active.id())).contains(active);
        assertThat(leads.count(active.id())).isEqualTo(1);
        assertThat(tasks.list(active.id())).hasSize(1);
        assertThat(activity.list(active.id())).hasSize(1);
        assertThat(deletion.deleteIfExpired(active.id(),now)).isFalse();
    }
}
