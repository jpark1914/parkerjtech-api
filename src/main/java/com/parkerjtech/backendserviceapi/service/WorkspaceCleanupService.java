package com.parkerjtech.backendserviceapi.service;

import java.time.Instant;
import org.springframework.stereotype.Service;
import com.parkerjtech.backendserviceapi.utils.CrmActionLogger;
import com.parkerjtech.backendserviceapi.repository.WorkspaceRepository;

@Service
public class WorkspaceCleanupService {
    private final WorkspaceRepository workspaces;
    private final WorkspaceCleanupTransaction deletion;
    private final CrmActionLogger actions;
    public WorkspaceCleanupService(WorkspaceRepository workspaces,WorkspaceCleanupTransaction deletion,CrmActionLogger actions) {
        this.workspaces=workspaces;this.deletion=deletion;this.actions=actions;
    }
    public int cleanupExpired(Instant cutoff,int batchSize) {
        actions.info("Service method started: WorkspaceCleanupService.cleanupExpired batchSize={}", batchSize);
        if(batchSize<1) throw new IllegalArgumentException("Cleanup batch size must be positive");
        actions.info("Expired demo workspace cleanup started; cutoffUTC={}, batchSize={}", cutoff, batchSize);
        var candidates = workspaces.findExpiredIds(cutoff,batchSize);
        actions.info("Found {} expired demo workspaces for cleanup", candidates.size());
        int deleted=0;
        for(var id:candidates) {
            if(deletion.deleteIfExpired(id,cutoff)) deleted++;
        }
        actions.info("Cleanup completed; removed {} expired demo workspaces", deleted);
        return deleted;
    }
}
