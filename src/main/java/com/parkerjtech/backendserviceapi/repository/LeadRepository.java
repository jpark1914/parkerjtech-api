package com.parkerjtech.backendserviceapi.repository;

import com.parkerjtech.backendserviceapi.model.Lead;
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
public class LeadRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final SqlQueries sql;
    private final CrmActionLogger actions;
    public LeadRepository(NamedParameterJdbcTemplate jdbc, SqlQueries sql, CrmActionLogger actions) { this.jdbc=jdbc; this.sql=sql; this.actions=actions; }
    private static final RowMapper<Lead> ROW=(r,n) -> new Lead(r.getObject("id",UUID.class),r.getObject("workspace_id",UUID.class),
        r.getString("name"),r.getString("company"),Lead.ServiceInterest.valueOf(r.getString("service_interest")),
        Lead.Source.valueOf(r.getString("source")),r.getBigDecimal("estimated_value"),
        r.getString("contact_preference")==null ? null : Lead.ContactPreference.valueOf(r.getString("contact_preference")),
        Lead.Stage.valueOf(r.getString("stage")),Lead.Priority.valueOf(r.getString("priority")),
        instant(r,"created_at"),instant(r,"updated_at"),instant(r,"last_activity_at"));
    public void insert(Lead lead) {
        actions.info("Repository method started: LeadRepository.insert leadId={} workspaceId={}", lead.id(), lead.workspaceId());
        int inserted=jdbc.update(sql.require("lead.insert"),new MapSqlParameterSource("id",lead.id())
            .addValue("workspaceId",lead.workspaceId()).addValue("name",lead.name()).addValue("company",lead.company())
            .addValue("serviceInterest",lead.serviceInterest().name()).addValue("source",lead.source().name())
            .addValue("estimatedValue",lead.estimatedValue()).addValue("contactPreference",lead.contactPreference()==null?null:lead.contactPreference().name())
            .addValue("stage",lead.stage().name()).addValue("priority",lead.priority().name())
            .addValue("createdAt",at(lead.createdAt())).addValue("updatedAt",at(lead.updatedAt())).addValue("lastActivityAt",at(lead.lastActivityAt())));
        if(inserted!=1) throw new DataIntegrityViolationException("Lead workspace does not exist");
    }
    public List<Lead> list(UUID workspaceId) {
        actions.info("Repository method started: LeadRepository.list workspaceId={}", workspaceId);
        return jdbc.query(sql.require("lead.list"),new MapSqlParameterSource("workspaceId",workspaceId),ROW);
    }
    public Optional<Lead> find(UUID workspaceId, UUID leadId) {
        actions.info("Repository method started: LeadRepository.find workspaceId={} leadId={}", workspaceId, leadId);
        return jdbc.query(sql.require("lead.find"),new MapSqlParameterSource("workspaceId",workspaceId).addValue("id",leadId),ROW).stream().findFirst();
    }
    public int count(UUID workspaceId) {
        actions.info("Repository method started: LeadRepository.count workspaceId={}", workspaceId);
        return jdbc.queryForObject(sql.require("lead.count"),new MapSqlParameterSource("workspaceId",workspaceId),Integer.class);
    }
    public int deleteByWorkspace(UUID workspaceId) {
        actions.info("Repository method started: LeadRepository.deleteByWorkspace workspaceId={}", workspaceId);
        return jdbc.update(sql.require("lead.deleteByWorkspace"),new MapSqlParameterSource("workspaceId",workspaceId));
    }
}
