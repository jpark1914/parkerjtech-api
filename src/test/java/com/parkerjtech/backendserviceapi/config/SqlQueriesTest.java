package com.parkerjtech.backendserviceapi.config;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import static org.assertj.core.api.Assertions.*;

class SqlQueriesTest {
    private ByteArrayResource resource(String value) {
        return new ByteArrayResource(value.getBytes(StandardCharsets.UTF_8));
    }
    @Test void rejectsMissingRequiredQuery() {
        assertThatThrownBy(() -> new SqlQueries(resource(""), Set.of("lead.insert")))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("lead.insert");
    }
    @Test void rejectsBlankRequiredQuery() {
        assertThatThrownBy(() -> new SqlQueries(resource("lead.insert=   "), Set.of("lead.insert")))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test void loadsContinuationLines() {
        var queries = new SqlQueries(resource("lead.find=SELECT * FROM crm_lead \\\n WHERE workspace_id=:workspaceId AND id=:leadId".replace("\\n", "\n")), Set.of("lead.find"));
        assertThat(queries.require("lead.find")).contains(":workspaceId", ":leadId");
        assertThatThrownBy(() -> queries.require("not.registered")).isInstanceOf(IllegalStateException.class);
    }
}
