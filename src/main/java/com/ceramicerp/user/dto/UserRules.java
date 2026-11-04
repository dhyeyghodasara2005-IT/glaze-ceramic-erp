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
    /**
     * No space (or tab, or line break) at the start or the end. Those are invisible, and a password copied out of a
     * PDF or a chat often brings one along; since none is allowed, login can simply ignore them (AuthService.login).
     */
    public static final String PASSWORD_ENDS = "^\\S(?:.*\\S)?$";
    public static final String PASSWORD_ENDS_MESSAGE = "A password can't start or end with a space";
}
