package com.ceramicerp.common.exception;

/** Thrown when the user is logged in but a rule forbids this specific action. Becomes HTTP 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
