package com.parkerjtech.backendserviceapi.integration;

import com.parkerjtech.backendserviceapi.model.ActivityEvent;
import com.parkerjtech.backendserviceapi.model.CrmTask;
import com.parkerjtech.backendserviceapi.model.Lead;
import com.parkerjtech.backendserviceapi.repository.ActivityRepository;
import com.parkerjtech.backendserviceapi.repository.LeadRepository;
import com.parkerjtech.backendserviceapi.repository.TaskRepository;
import com.parkerjtech.backendserviceapi.repository.WorkspaceRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static com.parkerjtech.backendserviceapi.support.DatabaseFixtures.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class JdbcRepositoriesTest {
    static final Instant NOW=Instant.parse("2026-11-11T12:00:00Z");
    @Autowired WorkspaceRepository workspaces;
    @Autowired LeadRepository leads;
    @Autowired TaskRepository tasks;
    @Autowired ActivityRepository activity;

    @Test void hidesLeadFromOtherWorkspace() {
        var a=workspace(workspaces,NOW); var b=workspace(workspaces,NOW);
        var lead=lead(leads,a,NOW,"Fictional Sam",new BigDecimal("4999.99"));
        assertThat(leads.find(b.id(),lead.id())).isEmpty();
        assertThat(leads.list(b.id())).isEmpty();
        assertThat(leads.count(b.id())).isZero();
        assertThat(leads.find(a.id(),lead.id())).contains(lead);
    }
    @Test void roundTripsSqlPunctuationAsText() {
        var a=workspace(workspaces,NOW);
        var lead=lead(leads,a,NOW,"Sam '); DROP TABLE crm_lead; --",new BigDecimal("4999.99"));
        assertThat(leads.find(a.id(),lead.id()).orElseThrow()).isEqualTo(lead);
        assertThat(leads.count(a.id())).isEqualTo(1);
        assertThat(workspaces.find(a.id())).contains(a);
    }
    @Test void rejectsTaskForOtherWorkspaceWithoutSwallowingFailure() {
        var a=workspace(workspaces,NOW); var b=workspace(workspaces,NOW);
        var lead=lead(leads,a,NOW,"Fictional Sam",new BigDecimal("10.00"));
        var bad=new CrmTask(UUID.randomUUID(),b.id(),lead.id(),"Demo",NOW,CrmTask.Status.OPEN,"INITIAL_FOLLOW_UP",NOW);
        assertThatThrownBy(() -> tasks.insertInitialIfAbsent(bad)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(tasks.list(a.id())).isEmpty();
    }
    @Test void rejectsActivityForOtherWorkspace() {
        var a=workspace(workspaces,NOW); var b=workspace(workspaces,NOW);
        var lead=lead(leads,a,NOW,"Fictional Sam",new BigDecimal("10.00"));
        var bad=new ActivityEvent(UUID.randomUUID(),b.id(),lead.id(),ActivityEvent.Type.LEAD_CREATED,"Demo",NOW);
        assertThatThrownBy(() -> activity.insert(bad)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void insertsInitialFollowUpOnlyOnceAndScopesLists() {
        var a=workspace(workspaces,NOW); var b=workspace(workspaces,NOW);
        var lead=lead(leads,a,NOW,"Fictional Sam",new BigDecimal("10.00"));
        workspaces.lock(a.id());
        var task=task(lead);
        assertThat(tasks.insertInitialIfAbsent(task)).isTrue();
        assertThat(tasks.insertInitialIfAbsent(task(lead))).isFalse();
        assertThat(tasks.list(a.id())).containsExactly(task);
        assertThat(tasks.list(b.id())).isEmpty();
        for(var type:new ActivityEvent.Type[]{ActivityEvent.Type.FOLLOW_UP_CREATED,ActivityEvent.Type.PRIORITY_ASSIGNED,ActivityEvent.Type.LEAD_CREATED}) activity.insert(event(lead,type));
        assertThat(activity.list(a.id())).extracting(ActivityEvent::type).containsExactly(ActivityEvent.Type.LEAD_CREATED,ActivityEvent.Type.PRIORITY_ASSIGNED,ActivityEvent.Type.FOLLOW_UP_CREATED);
        assertThat(activity.list(b.id())).isEmpty();
    }
    @Test void sortsLeadsNewestFirst() {
        var a=workspace(workspaces,NOW);
        var older=lead(leads,a,NOW,"Old Demo",new BigDecimal("10"));
        var newer=lead(leads,a,NOW.plusSeconds(1),"New Demo",new BigDecimal("10"));
        assertThat(leads.list(a.id())).extracting(Lead::id).containsExactly(newer.id(),older.id());
    }
    @Test void selectsExpiryAtDeadlineAndRequiresScopedDelete() {
        var a=workspace(workspaces,NOW); var b=workspace(workspaces,NOW.plusSeconds(1));
        assertThat(workspaces.findExpiredIds(a.expiresAt(),100)).containsExactly(a.id());
        assertThat(workspaces.deleteExpired(b.id(),a.expiresAt())).isZero();
        assertThat(workspaces.deleteExpired(a.id(),a.expiresAt())).isEqualTo(1);
        assertThat(workspaces.find(b.id())).contains(b);
    }
}
