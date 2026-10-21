package com.ceramicerp.auth.dto;

import java.time.Instant;

/** The frontend stores {@code token} and sends it as "Authorization: Bearer <token>". */
public record LoginResponse(
        String token,
        Instant expiresAt,
        CurrentUserResponse user
) {
}
