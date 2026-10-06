package com.alertamujer.backend.shared.errors;

import com.alertamujer.backend.shared.observability.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiFieldError> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed.", request, fields);
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleParameterValidation(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed.", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body is malformed.", request);
    }

    @ExceptionHandler({UnauthorizedException.class, AuthenticationException.class})
    public ResponseEntity<ApiError> handleUnauthorized(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required.", request);
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<ApiError> handleForbidden(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access is denied.", request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found.", request);
    }

    @ExceptionHandler(StateConflictException.class)
    public ResponseEntity<ApiError> handleConflict(StateConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "STATE_CONFLICT", "The requested operation conflicts with the current state.", request);
    }

    @ExceptionHandler(RuleViolationException.class)
    public ResponseEntity<ApiError> handleRuleViolation(RuleViolationException exception, HttpServletRequest request) {
        return response(HttpStatus.UNPROCESSABLE_CONTENT, "RULE_VIOLATION", "The request does not satisfy a business rule.", request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is invalid.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected request failure. requestId={}", RequestIdContext.getRequestId(request));
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.", request);
    }

    private ApiFieldError toFieldError(FieldError error) {
        return new ApiFieldError(error.getField(), safeMessage(error.getDefaultMessage()));
    }

    private String safeMessage(String message) {
        return message == null || message.isBlank() ? "Invalid value." : message;
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        return response(status, code, message, request, List.of());
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status, String code, String message, HttpServletRequest request, List<ApiFieldError> fields) {
        return ResponseEntity.status(status)
                .body(new ApiError(code, message, RequestIdContext.getRequestId(request),
                        fields.isEmpty() ? null : fields));
    }
}
