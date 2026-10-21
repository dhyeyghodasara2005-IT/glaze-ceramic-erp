package com.ceramicerp.organization;

import com.ceramicerp.organization.dto.CompanyRequest;
import com.ceramicerp.organization.dto.CompanyResponse;
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

/** HTTP endpoints for companies. Each method checks a permission, then calls the service. */
@Tag(name = "Companies")
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    @PreAuthorize("hasAuthority('COMPANY_VIEW')")
    public List<CompanyResponse> list() {
        return companyService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPANY_VIEW')")
    public CompanyResponse get(@PathVariable Long id) {
        return companyService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMPANY_MANAGE')")
    public CompanyResponse create(@Valid @RequestBody CompanyRequest req) {
        return companyService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPANY_MANAGE')")
    public CompanyResponse update(@PathVariable Long id, @Valid @RequestBody CompanyRequest req) {
        return companyService.update(id, req);
    }
}
