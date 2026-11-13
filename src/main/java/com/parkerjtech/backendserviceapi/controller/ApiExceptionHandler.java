package com.parkerjtech.backendserviceapi.controller;

import com.parkerjtech.backendserviceapi.exception.ApiException;
import com.parkerjtech.backendserviceapi.utils.ApiProblemWriter;
import com.parkerjtech.backendserviceapi.utils.CrmActionLogger;

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
import jakarta.servlet.http.HttpServletRequest;
import com.parkerjtech.backendserviceapi.utils.RequestLoggingFilter;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG=LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ApiProblemWriter problems;
    private final CrmActionLogger actions;
    public ApiExceptionHandler(ApiProblemWriter problems,CrmActionLogger actions) {
        this.problems=problems; this.actions=actions;
    }
    private void rejected(int status,String code,Exception ex,HttpServletRequest request) {
        LOG.warn("API request rejected localTime={} status={} code={}",actions.localTime(),status,code);
        LOG.error("API response stack trace localTime={} method={} path={} status={} code={}",
                actions.localTime(), request.getMethod(), request.getRequestURI(), status, code, ex);
        request.setAttribute(RequestLoggingFilter.STACK_TRACE_LOGGED_ATTRIBUTE, Boolean.TRUE);
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException ex,HttpServletRequest request) {
        rejected(ex.status(),ex.code(),ex,request);
        return response(problems.problem(ex.status(),ex.code(),ex.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidFields(MethodArgumentNotValidException ex,HttpServletRequest request) {
        rejected(400,"INVALID_REQUEST",ex,request);
        var detail=problems.problem(400,"INVALID_REQUEST","Some fields are invalid.");
        var errors=new TreeMap<String,String>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(),e.getDefaultMessage()));
        detail.setProperty("fieldErrors",errors);
        return response(detail);
    }
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> invalidCommand(ConstraintViolationException ex,HttpServletRequest request) {
        rejected(400,"INVALID_REQUEST",ex,request);
        var detail=problems.problem(400,"INVALID_REQUEST","Some fields are invalid.");
        var errors=new TreeMap<String,String>();
        ex.getConstraintViolations().forEach(e -> errors.putIfAbsent(e.getPropertyPath().toString(),e.getMessage()));
        detail.setProperty("fieldErrors",errors);
        return response(detail);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> invalidPayload(Exception ex,HttpServletRequest request) {
        rejected(400,"INVALID_REQUEST",ex,request);
        var detail = problems.problem(400,"INVALID_REQUEST","Request body or identifier is invalid.");
        var enumError = enumFieldError(ex);
        if (enumError != null) {
            detail.setDetail(enumError.field() == null
                    ? enumError.originalMessage()
                    : "One or more enum values are invalid.");
            detail.setProperty("message", detail.getDetail());
            if (enumError.field() != null) {
                detail.setProperty("fieldErrors", java.util.Map.of(enumError.field(), enumError.fieldMessage()));
            }
        }
        return response(detail);
    }

    private EnumFieldError enumFieldError(Exception ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (!cause.getClass().getName().endsWith(".InvalidFormatException")) continue;
            try {
                var targetType = cause.getClass().getMethod("getTargetType").invoke(cause);
                if (!(targetType instanceof Class<?> enumType) || !enumType.isEnum()) return null;
                Object path = cause.getClass().getMethod("getPath").invoke(cause);
                if (!(path instanceof List<?> references)) {
                    return originalEnumMessage(cause);
                }
                for (Object reference : references) {
                    Object fieldName = reference.getClass().getMethod("getFieldName").invoke(reference);
                    if (fieldName instanceof String field && !field.isBlank()) {
                        String accepted = java.util.Arrays.stream(enumType.getEnumConstants())
                                .map(Object::toString).collect(java.util.stream.Collectors.joining(", "));
                        return new EnumFieldError(field, "Must be one of: " + accepted + ".", safeMessage(cause));
                    }
                }
                return originalEnumMessage(cause);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return originalEnumMessage(cause);
            }
        }
        return null;
    }

    private EnumFieldError originalEnumMessage(Throwable cause) {
        return new EnumFieldError(null, null, safeMessage(cause));
    }

    private String safeMessage(Throwable cause) {
        return cause.getMessage() == null ? "The enum value is invalid." : cause.getMessage();
    }

    private record EnumFieldError(String field, String fieldMessage, String originalMessage) {}
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex,HttpServletRequest request) {
        if(ex instanceof org.springframework.web.ErrorResponse error && error.getStatusCode().is4xxClientError()) {
            rejected(error.getStatusCode().value(),"INVALID_REQUEST",ex,request);
            return ResponseEntity.status(error.getStatusCode()).headers(error.getHeaders()).header("Cache-Control","no-store")
                .body(problems.problem(error.getStatusCode().value(),"INVALID_REQUEST","Request cannot be handled by this endpoint."));
        }
        LOG.error("Unexpected API failure localTime={} status=500 exceptionType={}",actions.localTime(),ex.getClass().getName());
        LOG.error("Unexpected API failure stack trace localTime={} method={} path={}",
                actions.localTime(), request.getMethod(), request.getRequestURI(), ex);
        request.setAttribute(RequestLoggingFilter.STACK_TRACE_LOGGED_ATTRIBUTE, Boolean.TRUE);
        return response(problems.problem(500,"INTERNAL_ERROR","The request could not be completed."));
    }
    private ResponseEntity<ProblemDetail> response(ProblemDetail body) {
        body.setProperty("message", body.getDetail());
        return ResponseEntity.status(body.getStatus()).header("Cache-Control","no-store").body(body);
    }
}
