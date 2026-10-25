package com.ceramicerp.lab;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.lab.dto.GrnLabStatus;
import com.ceramicerp.lab.dto.LabForm;
import com.ceramicerp.lab.dto.LabMaterialRow;
import com.ceramicerp.lab.dto.LabOverview;
import com.ceramicerp.lab.dto.LabParameterResponse;
import com.ceramicerp.lab.dto.LabParametersRequest;
import com.ceramicerp.lab.dto.LabSample;
import com.ceramicerp.lab.dto.LabTestRequest;
import com.ceramicerp.lab.dto.LabTestResponse;
import com.ceramicerp.lab.dto.LabTestSummary;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * /api/lab - the lab tests raw material when it arrives (LAB_ENTRY); the store keeper, the manager and the owner see
 * the results (LAB_VIEW). Which tests a material gets is master data (MASTER_MANAGE).
 */
@Tag(name = "Lab")
@RestController
@RequestMapping("/api/lab")
@RequiredArgsConstructor
public class LabController {

    private static final String SEES = "hasAnyAuthority('LAB_VIEW', 'LAB_ENTRY')";

    private final LabService labService;

    @GetMapping("/materials")
    @PreAuthorize("hasAnyAuthority('LAB_VIEW', 'LAB_ENTRY', 'MASTER_VIEW')")
    public List<LabMaterialRow> materials() {
        return labService.materials();
    }

    @GetMapping("/materials/{materialId}/parameters")
    @PreAuthorize("hasAnyAuthority('LAB_VIEW', 'LAB_ENTRY', 'MASTER_VIEW')")
    public List<LabParameterResponse> parameters(@PathVariable Long materialId) {
        return labService.parameters(materialId);
    }

    @PutMapping("/materials/{materialId}/parameters")
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public List<LabParameterResponse> saveParameters(@PathVariable Long materialId, @Valid @RequestBody LabParametersRequest req) {
        return labService.saveParameters(materialId, req);
    }

    @GetMapping("/waiting")
    @PreAuthorize(SEES)
    public List<LabSample> waiting(@RequestParam Long plantId) {
        return labService.waiting(plantId);
    }

    @GetMapping("/form")
    @PreAuthorize(SEES)
    public LabForm form(@RequestParam Long documentId, @RequestParam Long materialId) {
        return labService.form(documentId, materialId);
    }

    @PostMapping("/tests")
    @PreAuthorize("hasAuthority('LAB_ENTRY')")
    public LabTestResponse save(@Valid @RequestBody LabTestRequest req) {
        return labService.save(req);
    }

    @GetMapping("/tests")
    @PreAuthorize(SEES)
    public PageResponse<LabTestSummary> search(@RequestParam Long plantId,
                                               @RequestParam(required = false) LabVerdict verdict,
                                               @RequestParam(required = false) Long materialId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(required = false) String search,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return labService.search(plantId, verdict, materialId, from, to, search, page, size);
    }

    @GetMapping("/tests/{id}")
    @PreAuthorize(SEES)
    public LabTestResponse get(@PathVariable Long id) {
        return labService.get(id);
    }

    @DeleteMapping("/tests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('LAB_ENTRY')")
    public void delete(@PathVariable Long id) {
        labService.delete(id);
    }

    @GetMapping("/overview")
    @PreAuthorize(SEES)
    public LabOverview overview(@RequestParam Long plantId) {
        return labService.overview(plantId);
    }

    /** Where each material of a GRN stands with the lab: the GRN page shows it to the store keeper. */
    @GetMapping("/grn/{documentId}")
    @PreAuthorize("hasAnyAuthority('LAB_VIEW', 'LAB_ENTRY', 'STORE_VIEW')")
    public List<GrnLabStatus> grnStatus(@PathVariable Long documentId) {
        return labService.grnStatus(documentId);
    }
}
