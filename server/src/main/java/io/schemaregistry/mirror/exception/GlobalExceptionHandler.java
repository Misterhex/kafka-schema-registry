package io.schemaregistry.mirror.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import io.schemaregistry.mirror.config.WebMvcConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final MediaType RESPONSE_CONTENT_TYPE = WebMvcConfig.SCHEMA_REGISTRY_V1_JSON_TYPE;

    @ExceptionHandler(SchemaRegistryException.class)
    public ResponseEntity<Map<String, Object>> handleSchemaRegistryException(SchemaRegistryException ex) {
        return buildErrorResponse(ex.getErrorCode(), ex.getMessage(), ex.getHttpStatus());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        String message = "Bad Request";
        Throwable cause = ex.getCause();
        if (cause instanceof JsonMappingException jme) {
            message = jme.getOriginalMessage();
        } else if (cause != null) {
            message = cause.getMessage();
        }
        return buildErrorResponse(400, message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return buildErrorResponse(415, ex.getMessage(), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoHandlerFoundException ex) {
        return buildErrorResponse(404, "Not Found", HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        return buildErrorResponse(50001, "Error in the backend data store - " + ex.getMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(int errorCode, String message, HttpStatus status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error_code", errorCode);
        body.put("message", message);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(RESPONSE_CONTENT_TYPE);

        return new ResponseEntity<>(body, headers, status);
    }
}
