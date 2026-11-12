package com.parkerjtech.backendserviceapi.config;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
public final class JdbcValues {
    private JdbcValues() {}
    public static OffsetDateTime at(Instant value) { return value.atOffset(ZoneOffset.UTC); }
    public static Instant instant(ResultSet row, String column) throws SQLException {
        return row.getObject(column, OffsetDateTime.class).toInstant();
    }
}
