package com.parkerjtech.backendserviceapi.service;

import com.parkerjtech.backendserviceapi.exception.ApiException;
import com.parkerjtech.backendserviceapi.utils.CrmActionLogger;
import com.parkerjtech.backendserviceapi.model.DemoWorkspace;
import com.parkerjtech.backendserviceapi.repository.WorkspaceRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {
    private final WorkspaceRepository workspaces;
    private final Clock clock;
    private final CrmActionLogger actions;
    public WorkspaceService(WorkspaceRepository workspaces,Clock clock,CrmActionLogger actions) {
        this.workspaces=workspaces; this.clock=clock; this.actions=actions;
    }
    @Transactional
    public DemoWorkspace bootstrap(UUID existingWorkspaceId) {
        actions.info("Service method started: WorkspaceService.bootstrap existingWorkspaceId={}", existingWorkspaceId);
        var now=clock.instant();
        if(existingWorkspaceId!=null) {
            var existing=workspaces.find(existingWorkspaceId).filter(w -> now.isBefore(w.expiresAt()));
            if(existing.isPresent()) {
                actions.afterCommit(() -> actions.info("Demo session recovered for workspace {}", existingWorkspaceId));
                return existing.get();
            }
            actions.info("Previous demo workspace {} is missing or expired; initializing a fresh demo session", existingWorkspaceId);
        }
        var workspace=new DemoWorkspace(UUID.randomUUID(),now,now.plus(Duration.ofHours(24)));
        workspaces.insert(workspace);
        actions.afterCommit(() -> actions.info("Demo workspace {} has been created for session initialization; expiresAtUTC={}",
                workspace.id(), workspace.expiresAt()));
        return workspace;
    }
    @Transactional(readOnly=true)
    public DemoWorkspace requireActive(UUID id) {
        actions.info("Service method started: WorkspaceService.requireActive workspaceId={}", id);
        if(id==null) throw ApiException.workspaceRequired();
        var workspace=workspaces.find(id).orElseThrow(ApiException::workspaceRequired);
        if(!clock.instant().isBefore(workspace.expiresAt())) {
            actions.warn("Demo workspace {} has expired at {}", id, workspace.expiresAt());
            throw ApiException.workspaceRequired();
        }
        actions.info("Demo workspace validated: {}", id);
        return workspace;
    }
}
