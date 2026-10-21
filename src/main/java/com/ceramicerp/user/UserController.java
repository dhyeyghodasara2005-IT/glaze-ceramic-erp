package com.ceramicerp.user;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.user.dto.ResetPasswordRequest;
import com.ceramicerp.user.dto.UserCreateRequest;
import com.ceramicerp.user.dto.UserResponse;
import com.ceramicerp.user.dto.UserUpdateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public PageResponse<UserResponse> search(@RequestParam(required = false) String search,
                                             @RequestParam(required = false) Boolean active,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return userService.search(search, active, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserResponse create(@Valid @RequestBody UserCreateRequest req) {
        return userService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest req) {
        return userService.update(id, req);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserResponse deactivate(@PathVariable Long id) {
        return userService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserResponse activate(@PathVariable Long id) {
        return userService.activate(id);
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserResponse resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest req) {
        return userService.resetPassword(id, req);
    }
}
