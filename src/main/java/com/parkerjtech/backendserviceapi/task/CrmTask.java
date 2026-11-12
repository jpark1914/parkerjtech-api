package com.parkerjtech.backendserviceapi.task;
import java.time.Instant;
import java.util.UUID;
public record CrmTask(UUID id, UUID workspaceId, UUID leadId, String description,
        Instant dueAt, Status status, String automationRuleKey, Instant createdAt) {
    public enum Status { OPEN }
}
