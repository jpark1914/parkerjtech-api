package com.parkerjtech.backendserviceapi.workspace;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.parkerjtech.backendserviceapi.api.ApiException;

@Service
public class WorkspaceService {
    private final WorkspaceRepository workspaces;
    private final Clock clock;
    public WorkspaceService(WorkspaceRepository workspaces,Clock clock) { this.workspaces=workspaces; this.clock=clock; }
    @Transactional
    public DemoWorkspace bootstrap(UUID existingWorkspaceId) {
        var now=clock.instant();
        if(existingWorkspaceId!=null) {
            var existing=workspaces.find(existingWorkspaceId).filter(w -> now.isBefore(w.expiresAt()));
            if(existing.isPresent()) return existing.get();
        }
        var workspace=new DemoWorkspace(UUID.randomUUID(),now,now.plus(Duration.ofHours(24)));
        workspaces.insert(workspace);
        return workspace;
    }
    @Transactional(readOnly=true)
    public DemoWorkspace requireActive(UUID id) {
        if(id==null) throw ApiException.workspaceRequired();
        var workspace=workspaces.find(id).orElseThrow(ApiException::workspaceRequired);
        if(!clock.instant().isBefore(workspace.expiresAt())) throw ApiException.workspaceRequired();
        return workspace;
    }
}
