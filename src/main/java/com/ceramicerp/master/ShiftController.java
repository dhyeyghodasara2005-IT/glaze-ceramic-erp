package com.ceramicerp.master;

import com.ceramicerp.master.dto.ShiftRequest;
import com.ceramicerp.master.dto.ShiftResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** HTTP endpoints for shifts: /api/masters/shifts */
@Tag(name = "Masters - Shifts")
@RestController
@RequestMapping("/api/masters/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService shiftService;

    @GetMapping
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public List<ShiftResponse> list() {
        return shiftService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public ShiftResponse get(@PathVariable Long id) {
        return shiftService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public ShiftResponse create(@Valid @RequestBody ShiftRequest req) {
        return shiftService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public ShiftResponse update(@PathVariable Long id, @Valid @RequestBody ShiftRequest req) {
        return shiftService.update(id, req);
    }
}
