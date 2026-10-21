package com.ceramicerp.master;

import com.ceramicerp.master.dto.GradeRequest;
import com.ceramicerp.master.dto.GradeResponse;
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

/** HTTP endpoints for grades: /api/masters/grades */
@Tag(name = "Masters - Grades")
@RestController
@RequestMapping("/api/masters/grades")
@RequiredArgsConstructor
public class GradeController {

    private final GradeService gradeService;

    @GetMapping
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public List<GradeResponse> list() {
        return gradeService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public GradeResponse get(@PathVariable Long id) {
        return gradeService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public GradeResponse create(@Valid @RequestBody GradeRequest req) {
        return gradeService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public GradeResponse update(@PathVariable Long id, @Valid @RequestBody GradeRequest req) {
        return gradeService.update(id, req);
    }
}
