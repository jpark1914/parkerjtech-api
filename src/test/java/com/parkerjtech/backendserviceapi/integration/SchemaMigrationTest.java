package com.parkerjtech.backendserviceapi.integration;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class SchemaMigrationTest {
    @Autowired JdbcTemplate jdbc;
    @Test void migratesDomainAndSessionTables() {
        for (String table : new String[]{"demo_workspace", "crm_lead", "crm_task", "crm_activity_event", "SPRING_SESSION", "SPRING_SESSION_ATTRIBUTES"}) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isZero();
        }
    }
    @Test void rejectsTaskForLeadInAnotherWorkspace() {
        var a = UUID.randomUUID(); var b = UUID.randomUUID(); var lead = UUID.randomUUID();
        for (var id : new UUID[]{a,b}) jdbc.update("INSERT INTO demo_workspace VALUES (?, CURRENT_TIMESTAMP, DATEADD('HOUR',24,CURRENT_TIMESTAMP))", id);
        jdbc.update("INSERT INTO crm_lead VALUES (?, ?, 'Fictional Name', 'Demo Co', 'WEBSITE', 'OTHER', 5000, NULL, 'NEW', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",lead,a);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO crm_task VALUES (?, ?, ?, 'Follow up', CURRENT_TIMESTAMP, 'OPEN', 'INITIAL_FOLLOW_UP', CURRENT_TIMESTAMP)",UUID.randomUUID(),b,lead))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void rejectsDuplicateInitialFollowUp() {
        var a = UUID.randomUUID(); var lead = UUID.randomUUID();
        jdbc.update("INSERT INTO demo_workspace VALUES (?, CURRENT_TIMESTAMP, DATEADD('HOUR',24,CURRENT_TIMESTAMP))", a);
        jdbc.update("INSERT INTO crm_lead VALUES (?, ?, 'Fictional Name', 'Demo Co', 'WEBSITE', 'OTHER', 5000, NULL, 'NEW', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",lead,a);
        var sql="INSERT INTO crm_task VALUES (?, ?, ?, 'Follow up', CURRENT_TIMESTAMP, 'OPEN', 'INITIAL_FOLLOW_UP', CURRENT_TIMESTAMP)";
        jdbc.update(sql,UUID.randomUUID(),a,lead);
        assertThatThrownBy(() -> jdbc.update(sql,UUID.randomUUID(),a,lead)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
