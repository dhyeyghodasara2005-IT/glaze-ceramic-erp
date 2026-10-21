package com.ceramicerp.auth;

import com.ceramicerp.auth.dto.ChangePasswordRequest;
import com.ceramicerp.auth.dto.CurrentUserResponse;
import com.ceramicerp.auth.dto.LoginRequest;
import com.ceramicerp.auth.dto.LoginResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Public endpoint (see SecurityConfig.PUBLIC_URLS). */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    /** Any logged-in user can call these - no special permission needed. */
    @GetMapping("/me")
    public CurrentUserResponse me() {
        return authService.me();
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(req);
    }
}
