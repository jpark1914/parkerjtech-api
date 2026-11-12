package com.parkerjtech.backendserviceapi.workspace;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DataIntegrityViolationException;
import com.parkerjtech.backendserviceapi.config.SqlQueries;
import static com.parkerjtech.backendserviceapi.config.JdbcValues.*;

@Repository
public class WorkspaceRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final SqlQueries sql;
    public WorkspaceRepository(NamedParameterJdbcTemplate jdbc, SqlQueries sql) { this.jdbc=jdbc; this.sql=sql; }
    private static final RowMapper<DemoWorkspace> ROW = (r,n) -> new DemoWorkspace(
        r.getObject("id",UUID.class),instant(r,"created_at"),instant(r,"expires_at"));
    public void insert(DemoWorkspace workspace) {
        jdbc.update(sql.require("workspace.insert"),new MapSqlParameterSource("id",workspace.id())
            .addValue("createdAt",at(workspace.createdAt())).addValue("expiresAt",at(workspace.expiresAt())));
    }
    public Optional<DemoWorkspace> find(UUID id) {
        return jdbc.query(sql.require("workspace.find"),new MapSqlParameterSource("id",id),ROW).stream().findFirst();
    }
    /** Caller must hold a transaction until all workspace work is finished. */
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<DemoWorkspace> lock(UUID id) {
        return jdbc.query(sql.require("workspace.lock"),new MapSqlParameterSource("id",id),ROW).stream().findFirst();
    }
    public List<UUID> findExpiredIds(Instant cutoff, int limit) {
        return jdbc.query(sql.require("workspace.expiredIds"),new MapSqlParameterSource("cutoff",at(cutoff)).addValue("limit",limit),
            (r,n) -> r.getObject("id",UUID.class));
    }
    public int deleteExpired(UUID id, Instant cutoff) {
        return jdbc.update(sql.require("workspace.deleteExpired"),new MapSqlParameterSource("id",id).addValue("cutoff",at(cutoff)));
    }
}
