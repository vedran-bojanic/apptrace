package io.apptrace.server.exception;

// Thrown when an API key is missing, invalid, revoked, or expired
public class InvalidApiKeyException extends RuntimeException {
    public InvalidApiKeyException(String message) {
        super(message);
    }
}
