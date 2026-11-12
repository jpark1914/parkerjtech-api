package com.parkerjtech.backendserviceapi.service;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.parkerjtech.backendserviceapi.model.Lead;
import static org.assertj.core.api.Assertions.*;
class PriorityPolicyTest {
    @ParameterizedTest @CsvSource({"0,LOW","999.99,LOW","1000,MEDIUM","4999.99,MEDIUM","5000,HIGH","1000000,HIGH"})
    void assignsPriorityAtThresholds(String amount,Lead.Priority expected) {
        assertThat(new PriorityPolicy().assign(new BigDecimal(amount))).isEqualTo(expected);
    }
}
