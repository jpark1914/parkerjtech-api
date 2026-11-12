package com.parkerjtech.backendserviceapi.repository;

import com.parkerjtech.backendserviceapi.model.CrmTask;
import com.parkerjtech.backendserviceapi.utils.JdbcValues;
import com.parkerjtech.backendserviceapi.utils.SqlQueries;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DataIntegrityViolationException;
import static com.parkerjtech.backendserviceapi.utils.JdbcValues.*;

@Repository
public class TaskRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final SqlQueries sql;
    public TaskRepository(NamedParameterJdbcTemplate jdbc, SqlQueries sql) { this.jdbc=jdbc; this.sql=sql; }
    private static final RowMapper<CrmTask> ROW=(r,n) -> new CrmTask(r.getObject("id",UUID.class),r.getObject("workspace_id",UUID.class),
        r.getObject("lead_id",UUID.class),r.getString("description"),instant(r,"due_at"),CrmTask.Status.valueOf(r.getString("status")),
        r.getString("automation_rule_key"),instant(r,"created_at"));
    /** Serialized by the caller's workspace row lock; uniqueness also enforced by the database. */
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public boolean insertInitialIfAbsent(CrmTask task) {
        boolean exists=list(task.workspaceId()).stream().anyMatch(t -> t.leadId().equals(task.leadId()) && t.automationRuleKey().equals(task.automationRuleKey()));
        if(exists) return false;
        int inserted=jdbc.update(sql.require("task.insertInitialIfAbsent"),new MapSqlParameterSource("id",task.id())
            .addValue("workspaceId",task.workspaceId()).addValue("leadId",task.leadId()).addValue("description",task.description())
            .addValue("dueAt",at(task.dueAt())).addValue("status",task.status().name())
            .addValue("automationRuleKey",task.automationRuleKey()).addValue("createdAt",at(task.createdAt())));
        if(inserted==1) return true;
        throw new DataIntegrityViolationException("Task lead does not belong to workspace");
    }
    public List<CrmTask> list(UUID workspaceId) {
        return jdbc.query(sql.require("task.list"),new MapSqlParameterSource("workspaceId",workspaceId),ROW);
    }
    public int deleteByWorkspace(UUID workspaceId) {
        return jdbc.update(sql.require("task.deleteByWorkspace"),new MapSqlParameterSource("workspaceId",workspaceId));
    }
}
