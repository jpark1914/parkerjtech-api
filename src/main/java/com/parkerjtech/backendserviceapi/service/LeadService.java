package com.parkerjtech.backendserviceapi.service;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.parkerjtech.backendserviceapi.model.*;
import com.parkerjtech.backendserviceapi.repository.*;
import com.parkerjtech.backendserviceapi.exception.ApiException;
import com.parkerjtech.backendserviceapi.utils.CrmActionLogger;

@Service
public class LeadService {
    private final WorkspaceRepository workspaces;
    private final LeadRepository leads;
    private final TaskRepository tasks;
    private final ActivityRepository activity;
    private final PriorityPolicy priorities;
    private final Clock clock;
    private final Validator validator;
    private final WorkspaceService workspaceService;
    private final CrmActionLogger actions;
    public LeadService(WorkspaceRepository workspaces,LeadRepository leads,TaskRepository tasks,
            ActivityRepository activity,PriorityPolicy priorities,Clock clock,Validator validator,WorkspaceService workspaceService,CrmActionLogger actions) {
        this.workspaces=workspaces; this.leads=leads; this.tasks=tasks; this.activity=activity;
        this.priorities=priorities; this.clock=clock; this.validator=validator; this.workspaceService=workspaceService; this.actions=actions;
    }
    @Transactional(readOnly=true)
    public java.util.List<Lead> list(UUID workspaceId) {
        workspaceService.requireActive(workspaceId);
        return leads.list(workspaceId);
    }
    @Transactional(readOnly=true)
    public Lead get(UUID workspaceId,UUID leadId) {
        workspaceService.requireActive(workspaceId);
        return leads.find(workspaceId,leadId).orElseThrow(() -> new ApiException(404,"LEAD_NOT_FOUND","Lead not found in this workspace."));
    }
    @Transactional
    public Lead create(UUID workspaceId,CreateLeadCommand command) {
        var workspace=workspaces.lock(workspaceId).orElseThrow(ApiException::workspaceRequired);
        // Read time only after acquiring the row lock: a waiting request may have expired.
        var now=clock.instant();
        if(!now.isBefore(workspace.expiresAt())) throw ApiException.workspaceRequired();
        if(leads.count(workspaceId)>=100) throw new ApiException(409,"WORKSPACE_LIMIT_REACHED","This demo workspace has reached its limit of 100 leads.");
        var violations=validator.validate(command);
        if(!violations.isEmpty()) throw new ConstraintViolationException(violations);
        var lead=new Lead(UUID.randomUUID(),workspaceId,command.name(),command.company(),command.serviceInterest(),command.source(),
            command.estimatedValue().setScale(2),command.contactPreference(),Lead.Stage.NEW,priorities.assign(command.estimatedValue()),now,now,now);
        leads.insert(lead);
        var task=new CrmTask(UUID.randomUUID(),workspaceId,lead.id(),"Follow up with fictional lead; internal demo task only, no message sent.",
            now.plus(Duration.ofHours(24)),CrmTask.Status.OPEN,"INITIAL_FOLLOW_UP",now);
        if(!tasks.insertInitialIfAbsent(task)) throw new IllegalStateException("New lead already has an initial follow-up");
        activity.insert(new ActivityEvent(UUID.randomUUID(),workspaceId,lead.id(),ActivityEvent.Type.LEAD_CREATED,"Fictional lead created.",now));
        var reason=switch(lead.priority()) {
            case HIGH -> "estimated value is at least USD 5,000";
            case MEDIUM -> "estimated value is at least USD 1,000 and below USD 5,000";
            case LOW -> "estimated value is below USD 1,000";
        };
        activity.insert(new ActivityEvent(UUID.randomUUID(),workspaceId,lead.id(),ActivityEvent.Type.PRIORITY_ASSIGNED,
            "Assigned "+lead.priority()+" priority because "+reason+".",now));
        activity.insert(new ActivityEvent(UUID.randomUUID(),workspaceId,lead.id(),ActivityEvent.Type.FOLLOW_UP_CREATED,
            "Created an internal follow-up task due in 24 hours. No message was sent.",now));
        actions.leadCreatedAfterCommit(lead);
        return lead;
    }
}
