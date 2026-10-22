package com.ceramicerp.master;

import com.ceramicerp.master.dto.BodyRecipeRequest;
import com.ceramicerp.master.dto.BodyRecipeResponse;
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

/** HTTP endpoints for body recipes: /api/masters/body-recipes */
@Tag(name = "Masters - Body recipes")
@RestController
@RequestMapping("/api/masters/body-recipes")
@RequiredArgsConstructor
public class BodyRecipeController {

    private final BodyRecipeService recipeService;

    @GetMapping
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public List<BodyRecipeResponse> list() {
        return recipeService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public BodyRecipeResponse get(@PathVariable Long id) {
        return recipeService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public BodyRecipeResponse create(@Valid @RequestBody BodyRecipeRequest req) {
        return recipeService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public BodyRecipeResponse update(@PathVariable Long id, @Valid @RequestBody BodyRecipeRequest req) {
        return recipeService.update(id, req);
    }
}
