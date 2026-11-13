package com.parkerjtech.backendserviceapi.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import com.parkerjtech.backendserviceapi.model.Lead;
import com.parkerjtech.backendserviceapi.utils.CrmActionLogger;

@Service
public class PriorityPolicy {
    private final CrmActionLogger actions;

    public PriorityPolicy(CrmActionLogger actions) {
        this.actions = actions;
    }

    public Lead.Priority assign(BigDecimal value) {
        actions.info("Service method started: PriorityPolicy.assign");
        if(value.compareTo(new BigDecimal("5000"))>=0) return Lead.Priority.HIGH;
        if(value.compareTo(new BigDecimal("1000"))>=0) return Lead.Priority.MEDIUM;
        return Lead.Priority.LOW;
    }
}
