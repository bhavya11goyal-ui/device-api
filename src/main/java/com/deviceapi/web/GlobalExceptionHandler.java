package com.deviceapi.web;

import com.deviceapi.exception.DeviceInUseException;
import com.deviceapi.exception.DeviceNameBrandLockedException;
import com.deviceapi.exception.DeviceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Scoped to {@code com.deviceapi.web} (i.e. our own controllers) rather
 * than a bare {@code @RestControllerAdvice}, which would apply globally -
 * including to Actuator's endpoints and Spring MVC's own infrastructure
 * (e.g. the 404 for an unmatched static resource). An unscoped catch-all
 * previously turned Actuator's legitimate 404s into misleading 500s.
 */
@RestControllerAdvice(basePackageClasses = DeviceController.class)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DeviceNotFoundException.class)
    public ProblemDetail handleNotFound(DeviceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Device Not Found");
        return problem;
    }

    @ExceptionHandler(DeviceInUseException.class)
    public ProblemDetail handleInUse(DeviceInUseException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Device In Use");
        return problem;
    }

    @ExceptionHandler(DeviceNameBrandLockedException.class)
    public ProblemDetail handleNameBrandLocked(DeviceNameBrandLockedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Device Locked");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation Failed");
        return problem;
    }

    /**
     * Thrown by Hibernate (via Spring's exception translation) when two
     * requests read the same device concurrently and both try to write -
     * see the {@code @Version} field on {@code Device} and
     * docs/DECISIONS.md #12. The client's view was stale by the time it
     * wrote; the fix from their side is to re-fetch and retry.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentModification(ObjectOptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Device was modified concurrently by another request; re-fetch and retry"
        );
        problem.setTitle("Concurrent Modification");
        return problem;
    }

    /**
     * Catch-all for anything not covered above. Deliberately returns a
     * generic detail message - internal exception details (stack traces,
     * SQL, etc.) must never reach the client - while still logging the
     * full exception server-side so unexpected failures are visible
     * rather than silently swallowed.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred"
        );
    }
}
