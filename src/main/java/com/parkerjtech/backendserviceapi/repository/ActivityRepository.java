package com.parkerjtech.backendserviceapi.repository;

import com.parkerjtech.backendserviceapi.model.ActivityEvent;
import com.parkerjtech.backendserviceapi.utils.JdbcValues;
import com.parkerjtech.backendserviceapi.utils.SqlQueries;
import com.parkerjtech.backendserviceapi.utils.CrmActionLogger;
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
public class ActivityRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final SqlQueries sql;
    private final CrmActionLogger actions;
    public ActivityRepository(NamedParameterJdbcTemplate jdbc, SqlQueries sql, CrmActionLogger actions) { this.jdbc=jdbc; this.sql=sql; this.actions=actions; }
    private static final RowMapper<ActivityEvent> ROW=(r,n) -> new ActivityEvent(r.getObject("id",UUID.class),r.getObject("workspace_id",UUID.class),
        r.getObject("lead_id",UUID.class),ActivityEvent.Type.valueOf(r.getString("type")),r.getString("description"),instant(r,"created_at"));
    public void insert(ActivityEvent event) {
        actions.info("Repository method started: ActivityRepository.insert eventId={} leadId={}", event.id(), event.leadId());
        int inserted=jdbc.update(sql.require("activity.insert"),new MapSqlParameterSource("id",event.id())
            .addValue("workspaceId",event.workspaceId()).addValue("leadId",event.leadId())
            .addValue("type",event.type().name()).addValue("description",event.description()).addValue("createdAt",at(event.createdAt())));
        if(inserted!=1) throw new DataIntegrityViolationException("Activity lead does not belong to workspace");
    }
    public List<ActivityEvent> list(UUID workspaceId) {
        actions.info("Repository method started: ActivityRepository.list workspaceId={}", workspaceId);
        return jdbc.query(sql.require("activity.list"),new MapSqlParameterSource("workspaceId",workspaceId),ROW);
    }
    public int deleteByWorkspace(UUID workspaceId) {
        actions.info("Repository method started: ActivityRepository.deleteByWorkspace workspaceId={}", workspaceId);
        return jdbc.update(sql.require("activity.deleteByWorkspace"),new MapSqlParameterSource("workspaceId",workspaceId));
    }
}
