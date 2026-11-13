package com.parkerjtech.backendserviceapi.controller;

import com.parkerjtech.backendserviceapi.exception.ApiException;
import com.parkerjtech.backendserviceapi.utils.ApiProblemWriter;
import com.parkerjtech.backendserviceapi.utils.CrmActionLogger;
import org.springframework.beans.factory.annotation.Value;

import java.util.TreeMap;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG=LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ApiProblemWriter problems;
    private final CrmActionLogger actions;
    private final boolean stackTraces;
    public ApiExceptionHandler(ApiProblemWriter problems,CrmActionLogger actions,
            @Value("${crm.logging.exception-stack-traces:false}") boolean stackTraces) {
        this.problems=problems; this.actions=actions; this.stackTraces=stackTraces;
    }
    private void rejected(int status,String code) {
        LOG.warn("API request rejected localTime={} status={} code={}",actions.localTime(),status,code);
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException ex) {
        rejected(ex.status(),ex.code());
        return response(problems.problem(ex.status(),ex.code(),ex.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidFields(MethodArgumentNotValidException ex) {
        rejected(400,"INVALID_REQUEST");
        var detail=problems.problem(400,"INVALID_REQUEST","Some fields are invalid.");
        var errors=new TreeMap<String,String>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(),e.getDefaultMessage()));
        detail.setProperty("fieldErrors",errors);
        return response(detail);
    }
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> invalidCommand(ConstraintViolationException ex) {
        rejected(400,"INVALID_REQUEST");
        var detail=problems.problem(400,"INVALID_REQUEST","Some fields are invalid.");
        var errors=new TreeMap<String,String>();
        ex.getConstraintViolations().forEach(e -> errors.putIfAbsent(e.getPropertyPath().toString(),e.getMessage()));
        detail.setProperty("fieldErrors",errors);
        return response(detail);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> invalidPayload(Exception ex) {
        rejected(400,"INVALID_REQUEST");
        return response(problems.problem(400,"INVALID_REQUEST","Request body or identifier is invalid."));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        if(ex instanceof org.springframework.web.ErrorResponse error && error.getStatusCode().is4xxClientError()) {
            rejected(error.getStatusCode().value(),"INVALID_REQUEST");
            return ResponseEntity.status(error.getStatusCode()).headers(error.getHeaders()).header("Cache-Control","no-store")
                .body(problems.problem(error.getStatusCode().value(),"INVALID_REQUEST","Request cannot be handled by this endpoint."));
        }
        LOG.error("Unexpected API failure localTime={} status=500 exceptionType={}",actions.localTime(),ex.getClass().getName());
        if(stackTraces) LOG.error("Unexpected API failure stack trace",ex);
        return response(problems.problem(500,"INTERNAL_ERROR","The request could not be completed."));
    }
    private ResponseEntity<ProblemDetail> response(ProblemDetail body) {
        return ResponseEntity.status(body.getStatus()).header("Cache-Control","no-store").body(body);
    }
}
