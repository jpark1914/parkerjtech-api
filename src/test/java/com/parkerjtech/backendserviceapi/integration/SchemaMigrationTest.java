package com.parkerjtech.backendserviceapi.integration;

import java.util.UUID;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariDataSource;
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
        assertThatThrownBy(() -> jdbc.update("INSERT INTO crm_task (id,workspace_id,lead_id,description,due_at,status,automation_rule_key,created_at,completed_at) VALUES (?, ?, ?, 'Follow up', CURRENT_TIMESTAMP, 'OPEN', 'INITIAL_FOLLOW_UP', CURRENT_TIMESTAMP,NULL)",UUID.randomUUID(),b,lead))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void rejectsDuplicateInitialFollowUp() {
        var a = UUID.randomUUID(); var lead = UUID.randomUUID();
        jdbc.update("INSERT INTO demo_workspace VALUES (?, CURRENT_TIMESTAMP, DATEADD('HOUR',24,CURRENT_TIMESTAMP))", a);
        jdbc.update("INSERT INTO crm_lead VALUES (?, ?, 'Fictional Name', 'Demo Co', 'WEBSITE', 'OTHER', 5000, NULL, 'NEW', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",lead,a);
        var sql="INSERT INTO crm_task (id,workspace_id,lead_id,description,due_at,status,automation_rule_key,created_at,completed_at) VALUES (?, ?, ?, 'Follow up', CURRENT_TIMESTAMP, 'OPEN', 'INITIAL_FOLLOW_UP', CURRENT_TIMESTAMP,NULL)";
        jdbc.update(sql,UUID.randomUUID(),a,lead);
        assertThatThrownBy(() -> jdbc.update(sql,UUID.randomUUID(),a,lead)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void upgradesPopulatedVersionTwoDatabaseWithoutLosingRows(@TempDir Path migrationDirectory) {
        var dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:file:" + migrationDirectory.resolve("mutations") + ";DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        var existing = new JdbcTemplate(dataSource);
        try {
        var flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("2")).load();
        flyway.migrate();
        var workspaceId = UUID.randomUUID();
        var leadId = UUID.randomUUID();
        var taskId = UUID.randomUUID();
        var activityId = UUID.randomUUID();
        existing.update("INSERT INTO demo_workspace VALUES (?, CURRENT_TIMESTAMP, DATEADD('HOUR',24,CURRENT_TIMESTAMP))", workspaceId);
        existing.update("INSERT INTO crm_lead VALUES (?, ?, 'Fictional Name', 'Demo Co', 'WEBSITE', 'OTHER', 5000, NULL, 'NEW', 'HIGH', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", leadId, workspaceId);
        existing.update("INSERT INTO crm_task (id,workspace_id,lead_id,description,due_at,status,automation_rule_key,created_at) VALUES (?, ?, ?, 'Follow up', CURRENT_TIMESTAMP, 'OPEN', 'INITIAL_FOLLOW_UP', CURRENT_TIMESTAMP)", taskId, workspaceId, leadId);
        existing.update("INSERT INTO crm_activity_event VALUES (?, ?, ?, 'LEAD_CREATED', 'Created', CURRENT_TIMESTAMP)", activityId, workspaceId, leadId);
        assertThat(existing.queryForList("SELECT constraint_name FROM information_schema.table_constraints WHERE table_name='CRM_TASK' AND constraint_type='CHECK'", String.class)).isNotEmpty();
        assertThat(existing.queryForList("SELECT constraint_name FROM information_schema.table_constraints WHERE table_name='CRM_ACTIVITY_EVENT' AND constraint_type='CHECK'", String.class)).isNotEmpty();

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        assertThat(existing.queryForObject("SELECT status FROM crm_task WHERE id=?", String.class, taskId)).isEqualTo("OPEN");
        assertThat(existing.queryForObject("SELECT completed_at FROM crm_task WHERE id=?", java.sql.Timestamp.class, taskId)).isNull();
        assertThat(existing.queryForObject("SELECT type FROM crm_activity_event WHERE id=?", String.class, activityId)).isEqualTo("LEAD_CREATED");
        assertThatThrownBy(() -> existing.update("UPDATE crm_task SET status='COMPLETED' WHERE id=?", taskId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> existing.update("UPDATE crm_task SET completed_at=CURRENT_TIMESTAMP WHERE id=?", taskId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(existing.update("UPDATE crm_task SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP WHERE id=?", taskId))
                .isEqualTo(1);
        assertThat(existing.update("INSERT INTO crm_activity_event (id,workspace_id,lead_id,type,description,created_at) VALUES (?, ?, ?, 'LEAD_STAGE_CHANGED', 'Stage changed', CURRENT_TIMESTAMP)",
                UUID.randomUUID(),workspaceId,leadId)).isEqualTo(1);
        assertThatThrownBy(() -> existing.update("INSERT INTO crm_activity_event (id,workspace_id,lead_id,type,description,created_at) VALUES (?, ?, ?, 'NOT_AN_EVENT', 'Bad', CURRENT_TIMESTAMP)",
                UUID.randomUUID(),workspaceId,leadId)).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            dataSource.close();
        }
    }
}
