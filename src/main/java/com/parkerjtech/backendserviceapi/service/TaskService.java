package com.parkerjtech.backendserviceapi.service;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.parkerjtech.backendserviceapi.utils.CrmActionLogger;
import org.springframework.transaction.annotation.Transactional;
import com.parkerjtech.backendserviceapi.model.CrmTask;
import com.parkerjtech.backendserviceapi.repository.TaskRepository;
@Service
public class TaskService {
    private final WorkspaceService workspaces;
    private final TaskRepository repository;
    private final CrmActionLogger actions;
    public TaskService(WorkspaceService workspaces,TaskRepository repository,CrmActionLogger actions) {
        this.workspaces=workspaces; this.repository=repository; this.actions=actions;
    }
    @Transactional(readOnly=true)
    public List<CrmTask> list(UUID workspaceId) {
        workspaces.requireActive(workspaceId);
        var results = repository.list(workspaceId);
        actions.info("Retrieved {} tasks from demo workspace {}", results.size(), workspaceId);
        results.forEach(task -> actions.info("Follow-up task {} has been retrieved for lead {}; status={}, dueAtUTC={}",
                task.id(), task.leadId(), task.status(), task.dueAt()));
        return results;
    }
}
