package com.deviceapi.web;

import com.deviceapi.exception.DeviceInUseException;
import com.deviceapi.exception.DeviceNameBrandLockedException;
import com.deviceapi.exception.DeviceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Scoped to {@code com.deviceapi.web} (i.e. our own controllers) rather
 * than a bare {@code @RestControllerAdvice}, which would apply globally -
 * including to Actuator's endpoints and Spring MVC's own infrastructure
 * (e.g. the 404 for an unmatched static resource).
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring MVC's own
 * well-typed request exceptions keep their correct 4xx statuses. Without
 * it, the catch-all {@code Exception} handler below swallowed all of
 * them and reported client mistakes - an unparseable UUID in the path, an
 * unknown enum value, malformed JSON - as 500s, blaming the server for
 * bad input.
 */
@RestControllerAdvice(basePackageClasses = DeviceController.class)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DeviceNotFoundException.class)
    public ProblemDetail handleNotFound(DeviceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Device Not Found");
        return problem;
    }

    @ExceptionHandler(DeviceInUseException.class)
    public ProblemDetail handleInUse(DeviceInUseException ex) {
        log.warn("Rejected delete of in-use device: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Device In Use");
        return problem;
    }

    @ExceptionHandler(DeviceNameBrandLockedException.class)
    public ProblemDetail handleNameBrandLocked(DeviceNameBrandLockedException ex) {
        log.warn("Rejected name/brand change on in-use device: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Device Locked");
        return problem;
    }

    /**
     * Thrown by Hibernate (via Spring's exception translation) when two
     * requests read the same device concurrently and both try to write -
     * see the {@code @Version} field on {@code Device} and
     * docs/DECISIONS.md #11. The client's view was stale by the time it
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
     * Raised by Spring Data when a {@code sort} query parameter names a
     * property the entity doesn't have - a client mistake, not a server
     * fault, so 400 rather than the 500 it would otherwise fall through to.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleUnknownSortProperty(PropertyReferenceException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Unknown sort property: '" + ex.getPropertyName() + "'"
        );
        problem.setTitle("Invalid Sort Property");
        return problem;
    }

    /**
     * Overrides the inherited handler (rather than declaring a second
     * {@code @ExceptionHandler} for the same type, which would be an
     * ambiguous mapping) to report every field error in one response
     * instead of only the first.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation Failed");
        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Last resort, for genuinely unexpected failures only - everything
     * Spring can classify is handled above or by the superclass.
     * Deliberately returns a generic detail message (internal exception
     * details must never reach the client) while logging the full
     * exception server-side so real faults stay visible.
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
