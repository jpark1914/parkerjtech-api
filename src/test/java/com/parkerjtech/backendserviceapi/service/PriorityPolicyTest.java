package com.parkerjtech.backendserviceapi.service;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import com.parkerjtech.backendserviceapi.utils.CrmActionLogger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.parkerjtech.backendserviceapi.model.Lead;
import static org.assertj.core.api.Assertions.*;
class PriorityPolicyTest {
    private final PriorityPolicy policy = new PriorityPolicy(new CrmActionLogger(
            Clock.fixed(Instant.parse("2026-11-13T18:00:00Z"), ZoneOffset.UTC), "America/Chicago"));
    @ParameterizedTest @CsvSource({"0,LOW","999.99,LOW","1000,MEDIUM","4999.99,MEDIUM","5000,HIGH","1000000,HIGH"})
    void assignsPriorityAtThresholds(String amount,Lead.Priority expected) {
        assertThat(policy.assign(new BigDecimal(amount))).isEqualTo(expected);
    }
}
