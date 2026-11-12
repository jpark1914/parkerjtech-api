package com.parkerjtech.backendserviceapi.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.parkerjtech.backendserviceapi.repository.*;

/** One workspace is locked and deleted in a single transaction. */
@Service
public class WorkspaceCleanupTransaction {
    private final WorkspaceRepository workspaces;
    private final LeadRepository leads;
    private final TaskRepository tasks;
    private final ActivityRepository activity;
    public WorkspaceCleanupTransaction(WorkspaceRepository workspaces,LeadRepository leads,TaskRepository tasks,ActivityRepository activity) {
        this.workspaces=workspaces;this.leads=leads;this.tasks=tasks;this.activity=activity;
    }
    @Transactional
    public boolean deleteIfExpired(UUID id,Instant cutoff) {
        var workspace=workspaces.lock(id);
        if(workspace.isEmpty() || workspace.get().expiresAt().isAfter(cutoff)) return false;
        activity.deleteByWorkspace(id);
        tasks.deleteByWorkspace(id);
        leads.deleteByWorkspace(id);
        if(workspaces.deleteExpired(id,cutoff)!=1) throw new IllegalStateException("Workspace changed during locked deletion");
        return true;
    }
}
