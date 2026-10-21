package com.ceramicerp.common.exception;

/** Thrown when a requested record does not exist. Becomes HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String entity, Object id) {
        super(entity + " not found: " + id);
    }
}
