package io.apptrace.server.exception;

// Thrown when the hash chain is broken — indicates tampering or a serious bug
public class ChainIntegrityException extends RuntimeException {
    public ChainIntegrityException(String message) {
        super(message);
    }
}
