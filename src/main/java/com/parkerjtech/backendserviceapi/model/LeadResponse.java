package com.parkerjtech.backendserviceapi.model;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record LeadResponse(UUID id,String name,String company,Lead.ServiceInterest serviceInterest,
        Lead.Source source,BigDecimal estimatedValue,Lead.ContactPreference contactPreference,
        Lead.Stage stage,Lead.Priority priority,Instant createdAt,Instant updatedAt,Instant lastActivityAt) {
    public static LeadResponse from(Lead lead) {
        return new LeadResponse(lead.id(),lead.name(),lead.company(),lead.serviceInterest(),lead.source(),lead.estimatedValue(),
            lead.contactPreference(),lead.stage(),lead.priority(),lead.createdAt(),lead.updatedAt(),lead.lastActivityAt());
    }
}
