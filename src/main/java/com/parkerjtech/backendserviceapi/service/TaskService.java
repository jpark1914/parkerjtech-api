package com.parkerjtech.backendserviceapi.service;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.parkerjtech.backendserviceapi.model.CrmTask;
import com.parkerjtech.backendserviceapi.repository.TaskRepository;
@Service
public class TaskService {
    private final WorkspaceService workspaces;
    private final TaskRepository repository;
    public TaskService(WorkspaceService workspaces,TaskRepository repository) { this.workspaces=workspaces; this.repository=repository; }
    @Transactional(readOnly=true)
    public List<CrmTask> list(UUID workspaceId) {
        workspaces.requireActive(workspaceId);
        return repository.list(workspaceId);
    }
}
