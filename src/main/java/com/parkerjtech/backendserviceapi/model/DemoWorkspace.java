package com.parkerjtech.backendserviceapi.model;
import java.time.Instant;
import java.util.UUID;
public record DemoWorkspace(UUID id, Instant createdAt, Instant expiresAt) {}
