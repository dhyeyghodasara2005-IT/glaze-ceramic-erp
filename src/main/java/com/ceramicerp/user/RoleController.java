package com.ceramicerp.user;

import com.ceramicerp.user.dto.PermissionResponse;
import com.ceramicerp.user.dto.RoleRequest;
import com.ceramicerp.user.dto.RoleResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Roles & Permissions")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    public List<PermissionResponse> permissions() {
        return roleService.listPermissions();
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    public List<RoleResponse> list() {
        return roleService.list();
    }

    @GetMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    public RoleResponse get(@PathVariable Long id) {
        return roleService.get(id);
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public RoleResponse create(@Valid @RequestBody RoleRequest req) {
        return roleService.create(req);
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public RoleResponse update(@PathVariable Long id, @Valid @RequestBody RoleRequest req) {
        return roleService.update(id, req);
    }

    @DeleteMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public void delete(@PathVariable Long id) {
        roleService.delete(id);
    }
}
