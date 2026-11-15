package com.parkerjtech.backendserviceapi.service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
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
        actions.info("Service method started: LeadService.list workspaceId={}", workspaceId);
        workspaceService.requireActive(workspaceId);
        var results = leads.list(workspaceId);
        actions.info("Retrieved {} leads from demo workspace {}", results.size(), workspaceId);
        results.forEach(lead -> actions.info("{} + {} has been retrieved as a lead", lead.name(), lead.id()));
        return results;
    }
    @Transactional(readOnly=true)
    public Lead get(UUID workspaceId,UUID leadId) {
        actions.info("Service method started: LeadService.get workspaceId={} leadId={}", workspaceId, leadId);
        workspaceService.requireActive(workspaceId);
        actions.info("Retrieving lead {} from demo workspace {}", leadId, workspaceId);
        var lead = leads.find(workspaceId,leadId).orElseThrow(() -> new ApiException(404,"LEAD_NOT_FOUND","Lead not found in this workspace."));
        actions.info("{} + {} has been retrieved as a lead", lead.name(), lead.id());
        return lead;
    }
    @Transactional
    public Lead create(UUID workspaceId,CreateLeadCommand command) {
        actions.info("Service method started: LeadService.create workspaceId={}", workspaceId);
        actions.info("Creating lead {} in demo workspace {}", command.name(), workspaceId);
        var workspace=workspaces.lock(workspaceId).orElseThrow(ApiException::workspaceRequired);
        // Read time only after acquiring the row lock: a waiting request may have expired.
        var now=clock.instant();
        if(!now.isBefore(workspace.expiresAt())) throw ApiException.workspaceRequired();
        actions.info("Demo workspace validated and locked for lead creation: {}", workspaceId);
        int currentCount = leads.count(workspaceId);
        actions.info("Lead capacity checked for demo workspace {}: current={}, limit=100", workspaceId, currentCount);
        if(currentCount>=100) throw new ApiException(409,"WORKSPACE_LIMIT_REACHED","This demo workspace has reached its limit of 100 leads.");
        var violations=validator.validate(command);
        if(!violations.isEmpty()) throw new ConstraintViolationException(violations);
        actions.info("Lead creation input validated for {}", command.name());
        var lead=new Lead(UUID.randomUUID(),workspaceId,command.name(),command.company(),command.serviceInterest(),command.source(),
            command.estimatedValue().setScale(2),command.contactPreference(),Lead.Stage.NEW,priorities.assign(command.estimatedValue()),now,now,now);
        leads.insert(lead);
        actions.leadCreatedAfterCommit(lead);
        actions.afterCommit(() -> actions.info("{} priority has been assigned to lead {} + {}", lead.priority(), lead.name(), lead.id()));
        var task=new CrmTask(UUID.randomUUID(),workspaceId,lead.id(),"Follow up with fictional lead; internal demo task only, no message sent.",
            now.plus(Duration.ofHours(24)),CrmTask.Status.OPEN,"INITIAL_FOLLOW_UP",now,null);
        if(!tasks.insertInitialIfAbsent(task)) throw new IllegalStateException("New lead already has an initial follow-up");
        actions.afterCommit(() -> actions.info("Initial follow-up task {} has been created for lead {}; status={}, dueAtUTC={}",
                task.id(), lead.id(), task.status(), task.dueAt()));
        recordActivity(new ActivityEvent(UUID.randomUUID(),workspaceId,lead.id(),ActivityEvent.Type.LEAD_CREATED,"Fictional lead created.",now));
        var reason=switch(lead.priority()) {
            case HIGH -> "estimated value is at least USD 5,000";
            case MEDIUM -> "estimated value is at least USD 1,000 and below USD 5,000";
            case LOW -> "estimated value is below USD 1,000";
        };
        recordActivity(new ActivityEvent(UUID.randomUUID(),workspaceId,lead.id(),ActivityEvent.Type.PRIORITY_ASSIGNED,
            "Assigned "+lead.priority()+" priority because "+reason+".",now));
        recordActivity(new ActivityEvent(UUID.randomUUID(),workspaceId,lead.id(),ActivityEvent.Type.FOLLOW_UP_CREATED,
            "Created an internal follow-up task due in 24 hours. No message was sent.",now));
        return lead;
    }

    @Transactional
    public Lead update(UUID workspaceId, UUID leadId, CreateLeadCommand command) {
        actions.info("Service method started: LeadService.update workspaceId={} leadId={}", workspaceId, leadId);
        var workspace=workspaces.lock(workspaceId).orElseThrow(ApiException::workspaceRequired);
        var now=clock.instant();
        if(!now.isBefore(workspace.expiresAt())) throw ApiException.workspaceRequired();
        var existing=leads.find(workspaceId,leadId)
                .orElseThrow(() -> new ApiException(404,"LEAD_NOT_FOUND","Lead not found in this workspace."));
        var violations=validator.validate(command);
        if(!violations.isEmpty()) throw new ConstraintViolationException(violations);

        var changedFields=new ArrayList<String>();
        if(!Objects.equals(existing.name(),command.name())) changedFields.add("name");
        if(!Objects.equals(existing.company(),command.company())) changedFields.add("company");
        if(existing.serviceInterest()!=command.serviceInterest()) changedFields.add("serviceInterest");
        if(existing.source()!=command.source()) changedFields.add("source");
        if(existing.estimatedValue().compareTo(command.estimatedValue())!=0) changedFields.add("estimatedValue");
        if(existing.contactPreference()!=command.contactPreference()) changedFields.add("contactPreference");
        var priority=priorities.assign(command.estimatedValue());
        boolean priorityChanged=existing.priority()!=priority;
        if(changedFields.isEmpty() && !priorityChanged) return existing;

        var updated=new Lead(existing.id(),existing.workspaceId(),command.name(),command.company(),command.serviceInterest(),
                command.source(),command.estimatedValue().setScale(2),command.contactPreference(),existing.stage(),priority,
                existing.createdAt(),now,now);
        if(leads.updateBusinessFields(updated)!=1) throw new IllegalStateException("Lead changed during locked update");
        if(!changedFields.isEmpty()) {
            recordActivity(new ActivityEvent(UUID.randomUUID(),workspaceId,leadId,ActivityEvent.Type.LEAD_UPDATED,
                    "Updated lead fields: "+String.join(", ",changedFields)+".",now));
        }
        if(priorityChanged) {
            String reason=switch(priority) {
                case HIGH -> "estimated value is at least USD 5,000";
                case MEDIUM -> "estimated value is at least USD 1,000 and below USD 5,000";
                case LOW -> "estimated value is below USD 1,000";
            };
            recordActivity(new ActivityEvent(UUID.randomUUID(),workspaceId,leadId,ActivityEvent.Type.PRIORITY_ASSIGNED,
                    "Assigned "+priority+" priority because "+reason+".",now));
        }
        actions.afterCommit(() -> actions.info("{} + {} has been updated as a lead",updated.name(),updated.id()));
        if(priorityChanged) actions.afterCommit(() -> actions.info("{} priority has been assigned to lead {} + {}",priority,updated.name(),updated.id()));
        return updated;
    }

    @Transactional
    public Lead changeStage(UUID workspaceId, UUID leadId, Lead.Stage stage) {
        actions.info("Service method started: LeadService.changeStage workspaceId={} leadId={} stage={}", workspaceId, leadId, stage);
        var workspace=workspaces.lock(workspaceId).orElseThrow(ApiException::workspaceRequired);
        var now=clock.instant();
        if(!now.isBefore(workspace.expiresAt())) throw ApiException.workspaceRequired();
        var existing=leads.find(workspaceId,leadId)
                .orElseThrow(() -> new ApiException(404,"LEAD_NOT_FOUND","Lead not found in this workspace."));
        if(existing.stage()==stage) return existing;
        if(leads.updateStage(workspaceId,leadId,stage,now)!=1) throw new IllegalStateException("Lead changed during locked stage update");
        var updated=new Lead(existing.id(),existing.workspaceId(),existing.name(),existing.company(),existing.serviceInterest(),
                existing.source(),existing.estimatedValue(),existing.contactPreference(),stage,existing.priority(),
                existing.createdAt(),now,now);
        recordActivity(new ActivityEvent(UUID.randomUUID(),workspaceId,leadId,ActivityEvent.Type.LEAD_STAGE_CHANGED,
                "Changed lead stage from "+existing.stage()+" to "+stage+".",now));
        actions.afterCommit(() -> actions.info("Lead {} + {} stage changed from {} to {}",updated.name(),updated.id(),existing.stage(),stage));
        return updated;
    }

    private void recordActivity(ActivityEvent event) {
        actions.info("Service method started: LeadService.recordActivity eventId={} leadId={}", event.id(), event.leadId());
        activity.insert(event);
        actions.afterCommit(() -> actions.info("{} activity event {} has been recorded for lead {}",
                event.type(), event.id(), event.leadId()));
    }
}
