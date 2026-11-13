package com.parkerjtech.backendserviceapi.model;
import java.time.Instant;
import java.util.UUID;
public record TaskResponse(UUID id,UUID leadId,String description,Instant dueAt,CrmTask.Status status,
        String automationRuleKey,Instant createdAt) {
    public static TaskResponse from(CrmTask task) {
        return new TaskResponse(task.id(),task.leadId(),task.description(),task.dueAt(),task.status(),task.automationRuleKey(),task.createdAt());
    }
}
