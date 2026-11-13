package com.parkerjtech.backendserviceapi.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.parkerjtech.backendserviceapi.utils.CrmActionLogger;
import org.springframework.transaction.annotation.Transactional;
import com.parkerjtech.backendserviceapi.repository.*;

/** One workspace is locked and deleted in a single transaction. */
@Service
public class WorkspaceCleanupTransaction {
    private final WorkspaceRepository workspaces;
    private final LeadRepository leads;
    private final TaskRepository tasks;
    private final ActivityRepository activity;
    private final CrmActionLogger actions;
    public WorkspaceCleanupTransaction(WorkspaceRepository workspaces,LeadRepository leads,TaskRepository tasks,ActivityRepository activity,CrmActionLogger actions) {
        this.workspaces=workspaces;this.leads=leads;this.tasks=tasks;this.activity=activity;this.actions=actions;
    }
    @Transactional
    public boolean deleteIfExpired(UUID id,Instant cutoff) {
        actions.info("Service method started: WorkspaceCleanupTransaction.deleteIfExpired workspaceId={}", id);
        var workspace=workspaces.lock(id);
        if(workspace.isEmpty() || workspace.get().expiresAt().isAfter(cutoff)) {
            actions.info("Cleanup skipped for demo workspace {}: missing or not expired", id);
            return false;
        }
        int eventCount = activity.deleteByWorkspace(id);
        int taskCount = tasks.deleteByWorkspace(id);
        int leadCount = leads.deleteByWorkspace(id);
        if(workspaces.deleteExpired(id,cutoff)!=1) throw new IllegalStateException("Workspace changed during locked deletion");
        actions.afterCommit(() -> {
            actions.info("Removed {} activity events from expired demo workspace {}", eventCount, id);
            actions.info("Removed {} tasks from expired demo workspace {}", taskCount, id);
            actions.info("Removed {} leads from expired demo workspace {}", leadCount, id);
            actions.info("Expired demo workspace {} has been deleted", id);
        });
        return true;
    }
}
