package io.apptrace.server.exception;

// Thrown when a tenant, API key, or event is not found
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
