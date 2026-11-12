package com.parkerjtech.backendserviceapi.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import com.parkerjtech.backendserviceapi.workspace.*;
import com.parkerjtech.backendserviceapi.lead.*;
import com.parkerjtech.backendserviceapi.task.*;
import com.parkerjtech.backendserviceapi.activity.*;

public final class DatabaseFixtures {
    private DatabaseFixtures() {}
    public static DemoWorkspace workspace(WorkspaceRepository repository, Instant createdAt) {
        var workspace = new DemoWorkspace(UUID.randomUUID(), createdAt, createdAt.plus(Duration.ofHours(24)));
        repository.insert(workspace);
        return workspace;
    }
    public static Lead lead(LeadRepository repository, DemoWorkspace workspace, Instant now, String name, BigDecimal value) {
        var lead = new Lead(UUID.randomUUID(), workspace.id(), name, "Fictional Co", Lead.ServiceInterest.WEBSITE,
            Lead.Source.REFERRAL, value, null, Lead.Stage.NEW, Lead.Priority.MEDIUM, now, now, now);
        repository.insert(lead);
        return lead;
    }
    public static CrmTask task(Lead lead) {
        return new CrmTask(UUID.randomUUID(), lead.workspaceId(), lead.id(), "Internal follow-up; no message sent",
            lead.createdAt().plus(Duration.ofHours(24)), CrmTask.Status.OPEN, "INITIAL_FOLLOW_UP", lead.createdAt());
    }
    public static ActivityEvent event(Lead lead, ActivityEvent.Type type) {
        return new ActivityEvent(UUID.randomUUID(), lead.workspaceId(), lead.id(), type, "Demo activity", lead.createdAt());
    }
}
