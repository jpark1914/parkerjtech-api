package com.parkerjtech.backendserviceapi.config;

import java.time.Clock;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class JdbcFoundationTest {
    @Autowired Clock clock;
    @Autowired DataSource dataSource;
    @Autowired NamedParameterJdbcTemplate jdbc;

    @Test void providesUtcClockAndIsolatedJdbcDatasource() throws Exception {
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:");
        }
        assertThat(jdbc.getJdbcOperations().queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    }
}
