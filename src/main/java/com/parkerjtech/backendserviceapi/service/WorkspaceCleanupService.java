package com.parkerjtech.backendserviceapi.service;

import java.time.Instant;
import org.springframework.stereotype.Service;
import com.parkerjtech.backendserviceapi.repository.WorkspaceRepository;

@Service
public class WorkspaceCleanupService {
    private final WorkspaceRepository workspaces;
    private final WorkspaceCleanupTransaction deletion;
    public WorkspaceCleanupService(WorkspaceRepository workspaces,WorkspaceCleanupTransaction deletion) {
        this.workspaces=workspaces;this.deletion=deletion;
    }
    public int cleanupExpired(Instant cutoff,int batchSize) {
        if(batchSize<1) throw new IllegalArgumentException("Cleanup batch size must be positive");
        int deleted=0;
        for(var id:workspaces.findExpiredIds(cutoff,batchSize)) {
            if(deletion.deleteIfExpired(id,cutoff)) deleted++;
        }
        return deleted;
    }
}
