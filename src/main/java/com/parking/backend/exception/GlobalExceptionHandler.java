package com.parking.backend.exception;

import com.parking.backend.dto.ApiErrorResponse;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

        private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

        // ===============================
        // ResponseStatusException
        // ===============================
        @ExceptionHandler(ResponseStatusException.class)
        public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
                        ResponseStatusException ex) {

                return ResponseEntity
                                .status(ex.getStatusCode())
                                .body(new ApiErrorResponse(
                                                false,
                                                ex.getReason()));
        }

        // ===============================
        // Validation
        // ===============================
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiErrorResponse> handleValidationException(
                        MethodArgumentNotValidException ex) {

                Map<String, String> errors = new HashMap<>();

                for (FieldError error : ex.getBindingResult().getFieldErrors()) {

                        errors.put(
                                        error.getField(),
                                        error.getDefaultMessage());
                }

                return ResponseEntity
                                .badRequest()
                                .body(new ApiErrorResponse(
                                                false,
                                                "Validation failed",
                                                errors));
        }

        // ===============================
        // Access Denied (403)
        // ===============================
        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ApiErrorResponse> handleAccessDenied(
                        AccessDeniedException ex) {

                logger.warn("Access denied: {}", ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body(new ApiErrorResponse(
                                                false,
                                                "Access denied"));
        }

        // ===============================
        // Database Errors
        // ===============================
        @ExceptionHandler(DataAccessException.class)
        public ResponseEntity<ApiErrorResponse> handleDatabaseException(
                        DataAccessException ex) {

                logger.error("Database error", ex);

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(new ApiErrorResponse(
                                                false,
                                                "Database operation failed"));
        }

        @ExceptionHandler(BusinessException.class)
        public ResponseEntity<ApiErrorResponse> handleBusinessException(
                        BusinessException ex) {

                logger.warn("Business exception: {}", ex.getMessage());

                return ResponseEntity
                                .status(ex.getStatus())
                                .body(new ApiErrorResponse(
                                                false,
                                                ex.getMessage()));
        }

        // ===============================
        // Runtime Exceptions
        // ===============================
        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<ApiErrorResponse> handleRuntimeException(
                        RuntimeException ex) {

                logger.warn("Runtime exception: {}", ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(new ApiErrorResponse(
                                                false,
                                                ex.getMessage()));
        }

        // ===============================
        // Unknown Exceptions
        // ===============================
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiErrorResponse> handleException(
                        Exception ex) {

                logger.error("Unhandled exception", ex);

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(new ApiErrorResponse(
                                                false,
                                                "Something went wrong. Please try again."));
        }
}