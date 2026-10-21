package com.ceramicerp.common.exception;

import java.time.Instant;
import java.util.Map;

/** The one JSON shape every error response uses, so the frontend handles errors in one place. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }
}
