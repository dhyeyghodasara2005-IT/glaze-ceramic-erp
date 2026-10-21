package com.ceramicerp.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Access the logged-in user from anywhere in a request: {@code CurrentUser.get()}. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AppUserDetails get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserDetails user) {
            return user;
        }
        throw new AuthenticationCredentialsNotFoundException("Not logged in");
    }
}
