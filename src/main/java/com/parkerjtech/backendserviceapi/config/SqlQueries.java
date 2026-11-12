package com.parkerjtech.backendserviceapi.config;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import org.springframework.core.io.Resource;

/** Explicit classpath query catalog; only SQL values are loaded, never interpolated. */
public final class SqlQueries {
    private final Properties queries = new Properties();
    public SqlQueries(Resource resource, Set<String> requiredKeys) {
        try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            queries.load(reader);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load SQL query catalog", ex);
        }
        requiredKeys.forEach(this::require);
    }
    public String require(String key) {
        var value = queries.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing SQL query: " + key);
        return value;
    }
}
