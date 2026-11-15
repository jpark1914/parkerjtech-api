package com.parkerjtech.backendserviceapi.model;

import jakarta.validation.constraints.NotNull;

public record CompleteTaskRequest(@NotNull TargetStatus status) {
    public enum TargetStatus { COMPLETED }
}
