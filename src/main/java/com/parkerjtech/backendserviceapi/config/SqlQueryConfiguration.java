package com.parkerjtech.backendserviceapi.config;

import com.parkerjtech.backendserviceapi.utils.SqlQueries;

import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration(proxyBeanMethods = false)
public class SqlQueryConfiguration {
    @Bean
    SqlQueries sqlQueries() {
        return new SqlQueries(new ClassPathResource("sqlqueries.props"), Set.of(
            "workspace.insert",
            "workspace.find",
            "workspace.lock",
            "workspace.expiredIds",
            "workspace.deleteExpired",
            "lead.insert",
            "lead.list",
            "lead.find",
            "lead.count",
            "lead.deleteByWorkspace",
            "task.insertInitialIfAbsent",
            "task.list",
            "task.deleteByWorkspace",
            "activity.insert",
            "activity.list",
            "activity.deleteByWorkspace"));
    }
}
