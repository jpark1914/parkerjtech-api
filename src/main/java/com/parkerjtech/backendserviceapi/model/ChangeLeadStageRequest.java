package com.parkerjtech.backendserviceapi.model;

import jakarta.validation.constraints.NotNull;

public record ChangeLeadStageRequest(@NotNull Lead.Stage stage) {}
