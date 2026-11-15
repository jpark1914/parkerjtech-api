package com.parkerjtech.backendserviceapi.service;
import java.util.List;
import java.util.UUID;
import java.time.Clock;
import com.parkerjtech.backendserviceapi.exception.ApiException;
import com.parkerjtech.backendserviceapi.model.ActivityEvent;
import com.parkerjtech.backendserviceapi.repository.ActivityRepository;
import com.parkerjtech.backendserviceapi.repository.LeadRepository;
import com.parkerjtech.backendserviceapi.repository.WorkspaceRepository;
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
    private final WorkspaceRepository workspaceRepository;
    private final LeadRepository leads;
    private final ActivityRepository activity;
    private final Clock clock;
    public TaskService(WorkspaceService workspaces,TaskRepository repository,CrmActionLogger actions,
            WorkspaceRepository workspaceRepository,LeadRepository leads,ActivityRepository activity,Clock clock) {
        this.workspaces=workspaces; this.repository=repository; this.actions=actions;
        this.workspaceRepository=workspaceRepository; this.leads=leads; this.activity=activity; this.clock=clock;
    }
    @Transactional(readOnly=true)
    public List<CrmTask> list(UUID workspaceId) {
        actions.info("Service method started: TaskService.list workspaceId={}", workspaceId);
        workspaces.requireActive(workspaceId);
        var results = repository.list(workspaceId);
        actions.info("Retrieved {} tasks from demo workspace {}", results.size(), workspaceId);
        results.forEach(task -> actions.info("Follow-up task {} has been retrieved for lead {}; status={}, dueAtUTC={}",
                task.id(), task.leadId(), task.status(), task.dueAt()));
        return results;
    }

    @Transactional
    public CrmTask complete(UUID workspaceId, UUID taskId) {
        actions.info("Service method started: TaskService.complete workspaceId={} taskId={}", workspaceId, taskId);
        var workspace=workspaceRepository.lock(workspaceId).orElseThrow(ApiException::workspaceRequired);
        var now=clock.instant();
        if(!now.isBefore(workspace.expiresAt())) throw ApiException.workspaceRequired();
        var existing=repository.find(workspaceId,taskId)
                .orElseThrow(() -> new ApiException(404,"TASK_NOT_FOUND","Follow-up task not found in this workspace."));
        if(existing.status()==CrmTask.Status.COMPLETED) return existing;
        if(repository.complete(workspaceId,taskId,now)!=1) throw new IllegalStateException("Task changed during locked completion");
        if(leads.touchActivity(workspaceId,existing.leadId(),now)!=1) throw new IllegalStateException("Task lead disappeared during locked completion");
        var completed=new CrmTask(existing.id(),existing.workspaceId(),existing.leadId(),existing.description(),existing.dueAt(),
                CrmTask.Status.COMPLETED,existing.automationRuleKey(),existing.createdAt(),now);
        activity.insert(new ActivityEvent(UUID.randomUUID(),workspaceId,existing.leadId(),ActivityEvent.Type.FOLLOW_UP_COMPLETED,
                "Completed follow-up task "+existing.id()+".",now));
        actions.afterCommit(() -> actions.info("Follow-up task {} has been completed for lead {}",completed.id(),completed.leadId()));
        return completed;
    }
}
