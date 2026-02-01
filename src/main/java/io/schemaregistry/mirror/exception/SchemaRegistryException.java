package io.schemaregistry.mirror.exception;

import org.springframework.http.HttpStatus;

public class SchemaRegistryException extends RuntimeException {

    private final int errorCode;
    private final HttpStatus httpStatus;

    public SchemaRegistryException(String message, int errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public SchemaRegistryException(String message, int errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    // --- 404 errors ---
    public static final String SUBJECT_NOT_FOUND_MESSAGE_FORMAT = "Subject '%s' not found.";
    public static final int SUBJECT_NOT_FOUND_ERROR_CODE = 40401;

    public static final String VERSION_NOT_FOUND_MESSAGE_FORMAT = "Version %s not found.";
    public static final int VERSION_NOT_FOUND_ERROR_CODE = 40402;

    public static final String SCHEMA_NOT_FOUND_MESSAGE = "Schema not found";
    public static final String SCHEMA_NOT_FOUND_MESSAGE_FORMAT = "Schema %s not found";
    public static final int SCHEMA_NOT_FOUND_ERROR_CODE = 40403;

    public static final String SUBJECT_SOFT_DELETED_MESSAGE_FORMAT =
        "Subject '%s' was soft deleted.Set permanent=true to delete permanently";
    public static final int SUBJECT_SOFT_DELETED_ERROR_CODE = 40404;

    public static final String SUBJECT_NOT_SOFT_DELETED_MESSAGE_FORMAT =
        "Subject '%s' was not deleted first before being permanently deleted";
    public static final int SUBJECT_NOT_SOFT_DELETED_ERROR_CODE = 40405;

    public static final String SCHEMAVERSION_SOFT_DELETED_MESSAGE_FORMAT =
        "Subject '%s' Version %s was soft deleted.Set permanent=true to delete permanently";
    public static final int SCHEMAVERSION_SOFT_DELETED_ERROR_CODE = 40406;

    public static final String SCHEMAVERSION_NOT_SOFT_DELETED_MESSAGE_FORMAT =
        "Subject '%s' Version %s was not deleted first before being permanently deleted";
    public static final int SCHEMAVERSION_NOT_SOFT_DELETED_ERROR_CODE = 40407;

    public static final String SUBJECT_LEVEL_COMPATIBILITY_NOT_CONFIGURED_MESSAGE_FORMAT =
        "Subject '%s' does not have subject-level compatibility configured";
    public static final int SUBJECT_LEVEL_COMPATIBILITY_NOT_CONFIGURED_ERROR_CODE = 40408;

    public static final String SUBJECT_LEVEL_MODE_NOT_CONFIGURED_MESSAGE_FORMAT =
        "Subject '%s' does not have subject-level mode configured";
    public static final int SUBJECT_LEVEL_MODE_NOT_CONFIGURED_ERROR_CODE = 40409;

    // --- 409 errors ---
    public static final int INCOMPATIBLE_SCHEMA_ERROR_CODE = 40901;

    // --- 422 errors ---
    public static final int INVALID_SCHEMA_ERROR_CODE = 42201;
    public static final int INVALID_VERSION_ERROR_CODE = 42202;
    public static final int INVALID_COMPATIBILITY_LEVEL_ERROR_CODE = 42203;
    public static final int INVALID_MODE_ERROR_CODE = 42204;
    public static final int OPERATION_NOT_PERMITTED_ERROR_CODE = 42205;
    public static final int REFERENCE_EXISTS_ERROR_CODE = 42206;
    public static final int ID_DOES_NOT_MATCH_ERROR_CODE = 42207;
    public static final int INVALID_SUBJECT_ERROR_CODE = 42208;

    // --- 500 errors ---
    public static final int STORE_ERROR_CODE = 50001;
    public static final int OPERATION_TIMEOUT_ERROR_CODE = 50002;
    public static final int REQUEST_FORWARDING_FAILED_ERROR_CODE = 50003;
    public static final int UNKNOWN_LEADER_ERROR_CODE = 50004;

    // --- Factory methods ---

    public static SchemaRegistryException subjectNotFoundException(String subject) {
        return new SchemaRegistryException(
            String.format(SUBJECT_NOT_FOUND_MESSAGE_FORMAT, subject),
            SUBJECT_NOT_FOUND_ERROR_CODE, HttpStatus.NOT_FOUND);
    }

    public static SchemaRegistryException versionNotFoundException(int version) {
        return new SchemaRegistryException(
            String.format(VERSION_NOT_FOUND_MESSAGE_FORMAT, version),
            VERSION_NOT_FOUND_ERROR_CODE, HttpStatus.NOT_FOUND);
    }

    public static SchemaRegistryException schemaNotFoundException() {
        return new SchemaRegistryException(
            SCHEMA_NOT_FOUND_MESSAGE,
            SCHEMA_NOT_FOUND_ERROR_CODE, HttpStatus.NOT_FOUND);
    }

    public static SchemaRegistryException schemaNotFoundException(int id) {
        return new SchemaRegistryException(
            String.format(SCHEMA_NOT_FOUND_MESSAGE_FORMAT, id),
            SCHEMA_NOT_FOUND_ERROR_CODE, HttpStatus.NOT_FOUND);
    }

    public static SchemaRegistryException subjectSoftDeletedException(String subject) {
        return new SchemaRegistryException(
            String.format(SUBJECT_SOFT_DELETED_MESSAGE_FORMAT, subject),
            SUBJECT_SOFT_DELETED_ERROR_CODE, HttpStatus.NOT_FOUND);
    }

    public static SchemaRegistryException subjectNotSoftDeletedException(String subject) {
        return new SchemaRegistryException(
            String.format(SUBJECT_NOT_SOFT_DELETED_MESSAGE_FORMAT, subject),
            SUBJECT_NOT_SOFT_DELETED_ERROR_CODE, HttpStatus.NOT_FOUND);
    }

    public static SchemaRegistryException schemaVersionSoftDeletedException(String subject, String version) {
        return new SchemaRegistryException(
            String.format(SCHEMAVERSION_SOFT_DELETED_MESSAGE_FORMAT, subject, version),
            SCHEMAVERSION_SOFT_DELETED_ERROR_CODE, HttpStatus.NOT_FOUND);
    }

    public static SchemaRegistryException schemaVersionNotSoftDeletedException(String subject, String version) {
        return new SchemaRegistryException(
            String.format(SCHEMAVERSION_NOT_SOFT_DELETED_MESSAGE_FORMAT, subject, version),
            SCHEMAVERSION_NOT_SOFT_DELETED_ERROR_CODE, HttpStatus.NOT_FOUND);
    }

    public static SchemaRegistryException subjectLevelCompatibilityNotConfigured(String subject) {
        return new SchemaRegistryException(
            String.format(SUBJECT_LEVEL_COMPATIBILITY_NOT_CONFIGURED_MESSAGE_FORMAT, subject),
            SUBJECT_LEVEL_COMPATIBILITY_NOT_CONFIGURED_ERROR_CODE, HttpStatus.NOT_FOUND);
    }

    public static SchemaRegistryException subjectLevelModeNotConfigured(String subject) {
        return new SchemaRegistryException(
            String.format(SUBJECT_LEVEL_MODE_NOT_CONFIGURED_MESSAGE_FORMAT, subject),
            SUBJECT_LEVEL_MODE_NOT_CONFIGURED_ERROR_CODE, HttpStatus.NOT_FOUND);
    }

    public static SchemaRegistryException incompatibleSchemaException(String message) {
        return new SchemaRegistryException(message, INCOMPATIBLE_SCHEMA_ERROR_CODE, HttpStatus.CONFLICT);
    }

    public static SchemaRegistryException invalidSchemaException(String message) {
        return new SchemaRegistryException(message, INVALID_SCHEMA_ERROR_CODE, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static SchemaRegistryException invalidVersionException(String version) {
        return new SchemaRegistryException(
            "The specified version '" + version + "' is not a valid version id. "
                + "Allowed values are between [1, 2^31-1] and the string \"latest\"",
            INVALID_VERSION_ERROR_CODE, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static SchemaRegistryException invalidCompatibilityLevelException(String level) {
        return new SchemaRegistryException(
            "Invalid compatibility level. Valid values are none, backward, "
                + "forward, full, backward_transitive, forward_transitive, and full_transitive",
            INVALID_COMPATIBILITY_LEVEL_ERROR_CODE, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static SchemaRegistryException invalidModeException(String mode) {
        return new SchemaRegistryException(
            "Invalid mode. Valid values are READWRITE, READONLY, READONLY_OVERRIDE, and IMPORT",
            INVALID_MODE_ERROR_CODE, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static SchemaRegistryException operationNotPermittedException(String message) {
        return new SchemaRegistryException(message, OPERATION_NOT_PERMITTED_ERROR_CODE, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static SchemaRegistryException referenceExistsException(String message) {
        return new SchemaRegistryException(message, REFERENCE_EXISTS_ERROR_CODE, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static SchemaRegistryException idDoesNotMatchException(String message) {
        return new SchemaRegistryException(message, ID_DOES_NOT_MATCH_ERROR_CODE, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static SchemaRegistryException invalidSubjectException(String subject) {
        return new SchemaRegistryException(
            "The specified subject '" + subject + "' is not valid.",
            INVALID_SUBJECT_ERROR_CODE, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static SchemaRegistryException storeException(String message, Throwable cause) {
        return new SchemaRegistryException(message, STORE_ERROR_CODE, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }

    public static SchemaRegistryException operationTimeoutException(String message) {
        return new SchemaRegistryException(message, OPERATION_TIMEOUT_ERROR_CODE, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public static SchemaRegistryException requestForwardingFailedException(String message, Throwable cause) {
        return new SchemaRegistryException(message, REQUEST_FORWARDING_FAILED_ERROR_CODE, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }

    public static SchemaRegistryException unknownLeaderException(String message) {
        return new SchemaRegistryException(message, UNKNOWN_LEADER_ERROR_CODE, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
