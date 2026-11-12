package com.parkerjtech.backendserviceapi.api;

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
    public ApiExceptionHandler(ApiProblemWriter problems) { this.problems=problems; }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException ex) {
        return response(problems.problem(ex.status(),ex.code(),ex.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidFields(MethodArgumentNotValidException ex) {
        var detail=problems.problem(400,"INVALID_REQUEST","Some fields are invalid.");
        var errors=new TreeMap<String,String>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(),e.getDefaultMessage()));
        detail.setProperty("fieldErrors",errors);
        return response(detail);
    }
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> invalidCommand(ConstraintViolationException ex) {
        var detail=problems.problem(400,"INVALID_REQUEST","Some fields are invalid.");
        var errors=new TreeMap<String,String>();
        ex.getConstraintViolations().forEach(e -> errors.putIfAbsent(e.getPropertyPath().toString(),e.getMessage()));
        detail.setProperty("fieldErrors",errors);
        return response(detail);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> invalidPayload(Exception ex) {
        return response(problems.problem(400,"INVALID_REQUEST","Request body or identifier is invalid."));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        // Log type, not input, SQL text, session identifiers or raw exception messages.
        LOG.error("Unexpected API failure of type {}",ex.getClass().getName());
        return response(problems.problem(500,"INTERNAL_ERROR","The request could not be completed."));
    }
    private ResponseEntity<ProblemDetail> response(ProblemDetail body) {
        return ResponseEntity.status(body.getStatus()).header("Cache-Control","no-store").body(body);
    }
}
