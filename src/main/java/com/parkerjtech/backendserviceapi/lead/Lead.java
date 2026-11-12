package com.parkerjtech.backendserviceapi.lead;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record Lead(UUID id, UUID workspaceId, String name, String company,
        ServiceInterest serviceInterest, Source source, BigDecimal estimatedValue,
        ContactPreference contactPreference, Stage stage, Priority priority,
        Instant createdAt, Instant updatedAt, Instant lastActivityAt) {
    public enum ServiceInterest { WEBSITE, CUSTOM_APPLICATION, BUSINESS_AUTOMATION }
    public enum Source { WEBSITE, SOCIAL_MEDIA, REFERRAL, OTHER }
    public enum ContactPreference { EMAIL, PHONE }
    public enum Stage { NEW, CONTACTED, QUALIFIED, WON, LOST }
    public enum Priority { LOW, MEDIUM, HIGH }
}
