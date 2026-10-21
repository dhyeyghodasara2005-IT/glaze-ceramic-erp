package com.ceramicerp.master;

import com.ceramicerp.master.dto.ProductCategoryRequest;
import com.ceramicerp.master.dto.ProductCategoryResponse;
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

/** HTTP endpoints for product categories: /api/masters/categories */
@Tag(name = "Masters - Categories")
@RestController
@RequestMapping("/api/masters/categories")
@RequiredArgsConstructor
public class ProductCategoryController {

    private final ProductCategoryService categoryService;

    @GetMapping
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public List<ProductCategoryResponse> list() {
        return categoryService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public ProductCategoryResponse get(@PathVariable Long id) {
        return categoryService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public ProductCategoryResponse create(@Valid @RequestBody ProductCategoryRequest req) {
        return categoryService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public ProductCategoryResponse update(@PathVariable Long id, @Valid @RequestBody ProductCategoryRequest req) {
        return categoryService.update(id, req);
    }
}
