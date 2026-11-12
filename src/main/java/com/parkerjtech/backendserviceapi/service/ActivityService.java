package com.parkerjtech.backendserviceapi.service;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.parkerjtech.backendserviceapi.model.ActivityEvent;
import com.parkerjtech.backendserviceapi.repository.ActivityRepository;
@Service
public class ActivityService {
    private final WorkspaceService workspaces;
    private final ActivityRepository repository;
    public ActivityService(WorkspaceService workspaces,ActivityRepository repository) { this.workspaces=workspaces; this.repository=repository; }
    @Transactional(readOnly=true)
    public List<ActivityEvent> list(UUID workspaceId) {
        workspaces.requireActive(workspaceId);
        return repository.list(workspaceId);
    }
}
