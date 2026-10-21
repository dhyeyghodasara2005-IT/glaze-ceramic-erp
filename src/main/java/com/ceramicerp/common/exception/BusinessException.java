package com.ceramicerp.common.exception;

/**
 * Thrown when a request breaks a business rule (duplicate code, deleting a role in use, ...).
 * Becomes HTTP 409 with the message shown to the user.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
