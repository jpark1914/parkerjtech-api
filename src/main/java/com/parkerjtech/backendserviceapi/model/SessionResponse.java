package com.parkerjtech.backendserviceapi.model;
import java.time.Instant;
import java.util.UUID;
public record SessionResponse(UUID workspaceId, Instant createdAt, Instant expiresAt, String csrfToken, String csrfHeaderName) {}
