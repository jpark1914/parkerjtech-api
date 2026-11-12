package com.parkerjtech.backendserviceapi.service;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name="crm.cleanup.enabled",havingValue="true",matchIfMissing=true)
public class WorkspaceCleanupScheduler {
    private final WorkspaceCleanupService cleanup;
    private final Clock clock;
    public WorkspaceCleanupScheduler(WorkspaceCleanupService cleanup,Clock clock) { this.cleanup=cleanup;this.clock=clock; }
    @Scheduled(fixedDelayString="${crm.cleanup.delay-ms:900000}")
    public void cleanUp() {
        cleanup.cleanupExpired(clock.instant(),100);
    }
}
