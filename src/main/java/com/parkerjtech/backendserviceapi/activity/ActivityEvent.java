package com.parkerjtech.backendserviceapi.activity;
import java.time.Instant;
import java.util.UUID;
public record ActivityEvent(UUID id, UUID workspaceId, UUID leadId, Type type,
        String description, Instant createdAt) {
    public enum Type { LEAD_CREATED, PRIORITY_ASSIGNED, FOLLOW_UP_CREATED }
}
