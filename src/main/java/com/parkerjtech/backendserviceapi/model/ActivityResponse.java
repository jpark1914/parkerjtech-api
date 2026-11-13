package com.parkerjtech.backendserviceapi.model;
import java.time.Instant;
import java.util.UUID;
public record ActivityResponse(UUID id,UUID leadId,ActivityEvent.Type type,String description,Instant createdAt) {
    public static ActivityResponse from(ActivityEvent event) {
        return new ActivityResponse(event.id(),event.leadId(),event.type(),event.description(),event.createdAt());
    }
}
