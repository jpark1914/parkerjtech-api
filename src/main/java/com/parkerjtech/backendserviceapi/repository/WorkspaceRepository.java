package com.parkerjtech.backendserviceapi.repository;

import com.parkerjtech.backendserviceapi.model.DemoWorkspace;
import com.parkerjtech.backendserviceapi.utils.JdbcValues;
import com.parkerjtech.backendserviceapi.utils.SqlQueries;
import com.parkerjtech.backendserviceapi.utils.CrmActionLogger;
import java.time.Instant;
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
public class WorkspaceRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final SqlQueries sql;
    private final CrmActionLogger actions;
    public WorkspaceRepository(NamedParameterJdbcTemplate jdbc, SqlQueries sql, CrmActionLogger actions) { this.jdbc=jdbc; this.sql=sql; this.actions=actions; }
    private static final RowMapper<DemoWorkspace> ROW = (r,n) -> new DemoWorkspace(
        r.getObject("id",UUID.class),instant(r,"created_at"),instant(r,"expires_at"));
    public void insert(DemoWorkspace workspace) {
        actions.info("Repository method started: WorkspaceRepository.insert workspaceId={}", workspace.id());
        jdbc.update(sql.require("workspace.insert"),new MapSqlParameterSource("id",workspace.id())
            .addValue("createdAt",at(workspace.createdAt())).addValue("expiresAt",at(workspace.expiresAt())));
    }
    public Optional<DemoWorkspace> find(UUID id) {
        actions.info("Repository method started: WorkspaceRepository.find workspaceId={}", id);
        return jdbc.query(sql.require("workspace.find"),new MapSqlParameterSource("id",id),ROW).stream().findFirst();
    }
    /** Caller must hold a transaction until all workspace work is finished. */
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<DemoWorkspace> lock(UUID id) {
        actions.info("Repository method started: WorkspaceRepository.lock workspaceId={}", id);
        return jdbc.query(sql.require("workspace.lock"),new MapSqlParameterSource("id",id),ROW).stream().findFirst();
    }
    public List<UUID> findExpiredIds(Instant cutoff, int limit) {
        actions.info("Repository method started: WorkspaceRepository.findExpiredIds limit={}", limit);
        return jdbc.query(sql.require("workspace.expiredIds"),new MapSqlParameterSource("cutoff",at(cutoff)).addValue("limit",limit),
            (r,n) -> r.getObject("id",UUID.class));
    }
    public int deleteExpired(UUID id, Instant cutoff) {
        actions.info("Repository method started: WorkspaceRepository.deleteExpired workspaceId={}", id);
        return jdbc.update(sql.require("workspace.deleteExpired"),new MapSqlParameterSource("id",id).addValue("cutoff",at(cutoff)));
    }
}
