package com.parkerjtech.backendserviceapi.utils;

import java.io.IOException;
import java.util.Map;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ApiProblemWriter {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(ApiProblemWriter.class);
    private final JsonMapper mapper;
    public ApiProblemWriter(JsonMapper mapper) { this.mapper=mapper; }
    public ProblemDetail problem(int status,String code,String detail) {
        var problem=ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatusCode.valueOf(status),detail);
        problem.setProperty("code",code);
        problem.setProperty("message",detail);
        problem.setProperty("fieldErrors",Map.of());
        return problem;
    }
    public void write(HttpServletResponse response,int status,String code,String detail) throws IOException {
        LOG.warn("Security request rejected status={} code={}",status,code);
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control","no-store");
        mapper.writeValue(response.getOutputStream(),problem(status,code,detail));
    }
}
