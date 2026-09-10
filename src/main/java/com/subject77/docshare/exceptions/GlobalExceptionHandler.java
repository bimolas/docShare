package com.subject77.docshare.exceptions;

import com.subject77.docshare.exceptions.dto.ErrorResponseDto;
import com.subject77.docshare.exceptions.dto.FieldErrorDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        ErrorResponseDto response = ErrorResponseDto.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(StorageFileNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleStorageFileNotFoundException(StorageFileNotFoundException ex, HttpServletRequest request) {
        log.warn("Storage file not found: {}", ex.getMessage());
        ErrorResponseDto response = ErrorResponseDto.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponseDto response = ErrorResponseDto.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(PermissionTamperedException.class)
    public ResponseEntity<ErrorResponseDto> handlePermissionTamperedException(PermissionTamperedException ex, HttpServletRequest request) {
        log.error("TAMPER ALERT on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponseDto response = ErrorResponseDto.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Security Violation")
                .message("Permission record integrity check failed. Request denied.")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(InvalidDocumentStateException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidDocumentStateException(InvalidDocumentStateException ex, HttpServletRequest request) {
        log.warn("Invalid document state: {}", ex.getMessage());
        ErrorResponseDto response = ErrorResponseDto.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.CONFLICT.value())
                .error(HttpStatus.CONFLICT.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class})
    public ResponseEntity<ErrorResponseDto> handleOptimisticLockingFailureException(Exception ex, HttpServletRequest request) {
        log.warn("Optimistic locking conflict on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponseDto response = ErrorResponseDto.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.CONFLICT.value())
                .error(HttpStatus.CONFLICT.getReasonPhrase())
                .message("The resource was updated concurrently by another operation. Please refresh and try again.")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponseDto> handleStorageException(StorageException ex, HttpServletRequest request) {
        log.error("Storage infrastructure error on path {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponseDto response = ErrorResponseDto.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("Storage Service Error")
                .message("An error occurred while communicating with the document storage service. Please try again later.")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(BlockchainAnchoringException.class)
    public ResponseEntity<ErrorResponseDto> handleBlockchainAnchoringException(BlockchainAnchoringException ex, HttpServletRequest request) {
        log.error("Blockchain anchoring error on path {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponseDto response = ErrorResponseDto.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_GATEWAY.value())
                .error("Blockchain Service Error")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation failed for request to {}: {}", request.getRequestURI(), ex.getMessage());
        List<FieldErrorDto> validationErrors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            validationErrors.add(FieldErrorDto.builder()
                    .field(fieldError.getField())
                    .message(fieldError.getDefaultMessage())
                    .rejectedValue(fieldError.getRejectedValue())
                    .build());
        }

        ErrorResponseDto response = ErrorResponseDto.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Input validation failed for request body fields")
                .path(request.getRequestURI())
                .validationErrors(validationErrors)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponseDto response = ErrorResponseDto.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalStateException(IllegalStateException ex, HttpServletRequest request) {
        log.warn("Illegal state on path {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponseDto response = ErrorResponseDto.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGlobalException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled internal server error on path {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponseDto response = ErrorResponseDto.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("An unexpected internal error occurred. Please contact system support.")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
