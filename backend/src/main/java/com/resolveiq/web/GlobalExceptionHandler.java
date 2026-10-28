package com.resolveiq.web;

import com.resolveiq.common.exception.ErrorCode;
import com.resolveiq.common.exception.ItsmException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates every failure into an RFC 7807 "problem details" JSON response with a stable
 * {@code code}, so the frontend can react precisely (e.g. show duplicate matches on DUPLICATE_INCIDENT)
 * and stack traces never leak to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ItsmException.class)
    ResponseEntity<ProblemDetail> handleBusiness(ItsmException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(ex.getErrorCode(), ex.getMessage(), request);
        ex.details().forEach(problem::setProperty);
        return ResponseEntity.status(ex.getErrorCode().status()).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED, "Some fields are invalid", request);
        problem.setProperty("fieldErrors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED, "Some parameters are invalid", request);
        problem.setProperty("fieldErrors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, IllegalArgumentException.class})
    ResponseEntity<ProblemDetail> handleBadInput(Exception ex, HttpServletRequest request) {
        String detail = ex instanceof HttpMessageNotReadableException
                ? "Malformed request body or an invalid value for an enumerated field" : ex.getMessage();
        return ResponseEntity.badRequest().body(problem(ErrorCode.VALIDATION_FAILED, detail, request));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ResponseEntity<ProblemDetail> handleDenied(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem(ErrorCode.UNAUTHORIZED_OPERATION,
                "Your role is not allowed to perform this operation", request));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    ResponseEntity<ProblemDetail> handleConcurrent(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(ErrorCode.CONCURRENT_MODIFICATION,
                "Someone else changed this record at the same time. Refresh and try again.", request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(ErrorCode.DUPLICATE_RESOURCE,
                "The change conflicts with existing data (duplicate or still referenced)", request));
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    ResponseEntity<ProblemDetail> handleDatabaseDown(Exception ex, HttpServletRequest request) {
        log.error("Database unavailable", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem(ErrorCode.DATABASE_UNAVAILABLE,
                "The database is temporarily unavailable. Please try again shortly.", request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> handleNoRoute(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem(ErrorCode.RESOURCE_NOT_FOUND,
                "No such endpoint", request));
    }

    /** A browser closed its live-update stream (tab closed, laptop asleep). Nothing to report. */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    void handleClientGone() {
        // the response can no longer be written; ignore
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem(ErrorCode.INTERNAL_ERROR,
                "Something went wrong on our side. The error has been logged.", request));
    }

    private static ProblemDetail problem(ErrorCode code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.title());
        problem.setProperty("code", code.name());
        problem.setProperty("path", request.getRequestURI());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
