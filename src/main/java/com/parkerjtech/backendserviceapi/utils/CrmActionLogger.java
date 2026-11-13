package com.parkerjtech.backendserviceapi.utils;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.parkerjtech.backendserviceapi.model.Lead;

/** Human-readable console events, without changing UTC persistence timestamps. */
@Component
public class CrmActionLogger {
    private static final Logger LOG = LoggerFactory.getLogger(CrmActionLogger.class);
    private final Clock clock;
    private final ZoneId zone;

    public CrmActionLogger(Clock clock, @Value("${crm.logging.zone:America/Chicago}") String zone) {
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    public String localTime() {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(clock.instant().atZone(zone));
    }

    public static String singleLine(String value) {
        if (value == null) return "-";
        String text = value.replaceAll("[\\p{Cntrl}\\u2028\\u2029]", " ");
        return text.substring(0, Math.min(text.length(), 200));
    }

    public void info(String message, Object... values) {
        LOG.info("localTime={} " + message, timestamped(values));
    }

    public void warn(String message, Object... values) {
        LOG.warn("localTime={} " + message, timestamped(values));
    }

    private Object[] timestamped(Object[] values) {
        Object[] parameters = new Object[values.length + 1];
        parameters[0] = localTime();
        for (int i = 0; i < values.length; i++) {
            parameters[i + 1] = values[i] instanceof String text ? singleLine(text) : values[i];
        }
        return parameters;
    }

    public void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Committed action logging requires a transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    public void leadCreatedAfterCommit(Lead lead) {
        afterCommit(() -> info("{} + {} has been created as a lead", lead.name(), lead.id()));
    }
}
