package com.subject77.docshare.exceptions;

import com.subject77.docshare.exceptions.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        exceptionHandler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/v1/test");
    }

    @Test
    @DisplayName("Should return 404 NOT_FOUND for ResourceNotFoundException")
    void handleResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User not found");
        ResponseEntity<ErrorResponseDto> response = exceptionHandler.handleResourceNotFoundException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("User not found");
        assertThat(response.getBody().getPath()).isEqualTo("/api/v1/test");
    }

    @Test
    @DisplayName("Should return 404 NOT_FOUND for StorageFileNotFoundException")
    void handleStorageFileNotFoundException() {
        StorageFileNotFoundException ex = new StorageFileNotFoundException("File missing");
        ResponseEntity<ErrorResponseDto> response = exceptionHandler.handleStorageFileNotFoundException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("Should return 403 FORBIDDEN for PermissionTamperedException with security violation alert")
    void handlePermissionTamperedException() {
        PermissionTamperedException ex = new PermissionTamperedException("Integrity failed");
        ResponseEntity<ErrorResponseDto> response = exceptionHandler.handlePermissionTamperedException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Security Violation");
    }

    @Test
    @DisplayName("Should return 409 CONFLICT for InvalidDocumentStateException")
    void handleInvalidDocumentStateException() {
        InvalidDocumentStateException ex = new InvalidDocumentStateException("Cannot download pending file");
        ResponseEntity<ErrorResponseDto> response = exceptionHandler.handleInvalidDocumentStateException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("Should return 503 SERVICE_UNAVAILABLE for StorageException")
    void handleStorageException() {
        StorageException ex = new StorageException("S3 Connection failed");
        ResponseEntity<ErrorResponseDto> response = exceptionHandler.handleStorageException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Storage Service Error");
    }

    @Test
    @DisplayName("Should return 502 BAD_GATEWAY for BlockchainAnchoringException")
    void handleBlockchainAnchoringException() {
        BlockchainAnchoringException ex = new BlockchainAnchoringException("Ganache RPC unreachable");
        ResponseEntity<ErrorResponseDto> response = exceptionHandler.handleBlockchainAnchoringException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Blockchain Service Error");
    }
}
