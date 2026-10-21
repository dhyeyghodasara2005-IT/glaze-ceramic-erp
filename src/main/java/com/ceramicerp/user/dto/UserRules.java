package com.ceramicerp.user.dto;

/** Validation constants shared by user/auth requests. */
public final class UserRules {

    private UserRules() {
    }

    public static final String USERNAME = "^[A-Za-z0-9._-]{3,50}$";
    public static final String LANGUAGE = "^(en|gu|hi)$";
    public static final int PASSWORD_MIN = 8;
    /** BCrypt only uses the first 72 bytes of a password. */
    public static final int PASSWORD_MAX = 72;
}
