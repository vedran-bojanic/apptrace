package io.apptrace.server.exception;

// Thrown when trying to create a tenant with an externalId that already exists
public class TenantAlreadyExistsException extends RuntimeException {
    public TenantAlreadyExistsException(String message) {
        super(message);
    }
}
