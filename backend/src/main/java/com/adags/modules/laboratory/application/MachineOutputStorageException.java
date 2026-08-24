package com.adags.modules.laboratory.application;

// Wraps a MinIO SDK failure (network, credentials, bucket) behind one
// unchecked type — GlobalExceptionHandler maps it to a 502, distinct from
// the domain-validation exceptions (MissingMachineOutputException etc.)
// that map to 4xx: this is an infrastructure failure, not a bad request.
public class MachineOutputStorageException extends RuntimeException {
    public MachineOutputStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
