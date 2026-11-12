package com.parkerjtech.backendserviceapi.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import com.parkerjtech.backendserviceapi.model.Lead;

@Service
public class PriorityPolicy {
    public Lead.Priority assign(BigDecimal value) {
        if(value.compareTo(new BigDecimal("5000"))>=0) return Lead.Priority.HIGH;
        if(value.compareTo(new BigDecimal("1000"))>=0) return Lead.Priority.MEDIUM;
        return Lead.Priority.LOW;
    }
}
