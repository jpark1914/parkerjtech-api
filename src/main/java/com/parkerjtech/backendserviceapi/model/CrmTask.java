package com.parkerjtech.backendserviceapi.model;
import java.time.Instant;
import java.util.UUID;
public record CrmTask(UUID id, UUID workspaceId, UUID leadId, String description,
        Instant dueAt, Status status, String automationRuleKey, Instant createdAt, Instant completedAt) {
    public enum Status { OPEN, COMPLETED }
}
