package com.parkerjtech.backendserviceapi.service;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.parkerjtech.backendserviceapi.utils.CrmActionLogger;
import org.springframework.transaction.annotation.Transactional;
import com.parkerjtech.backendserviceapi.model.ActivityEvent;
import com.parkerjtech.backendserviceapi.repository.ActivityRepository;
@Service
public class ActivityService {
    private final WorkspaceService workspaces;
    private final ActivityRepository repository;
    private final CrmActionLogger actions;
    public ActivityService(WorkspaceService workspaces,ActivityRepository repository,CrmActionLogger actions) {
        this.workspaces=workspaces; this.repository=repository; this.actions=actions;
    }
    @Transactional(readOnly=true)
    public List<ActivityEvent> list(UUID workspaceId) {
        workspaces.requireActive(workspaceId);
        var results = repository.list(workspaceId);
        actions.info("Retrieved {} activity events from demo workspace {}", results.size(), workspaceId);
        results.forEach(event -> actions.info("Activity event {} has been retrieved for lead {}; type={}",
                event.id(), event.leadId(), event.type()));
        return results;
    }
}
