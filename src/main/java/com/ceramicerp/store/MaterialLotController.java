package com.ceramicerp.store;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.store.dto.DocumentLot;
import com.ceramicerp.store.dto.MaterialLotDetail;
import com.ceramicerp.store.dto.MaterialLotRow;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * /api/store/lots?plantId=1              - raw material lot by lot (which truck is what's left from)
 * /api/store/lots/5                      - one lot and what used it
 * /api/store/lots/of-document/12         - the lots behind a GRN or an issue
 */
@Tag(name = "Store - Material lots")
@RestController
@RequestMapping("/api/store/lots")
@RequiredArgsConstructor
public class MaterialLotController {

    private final MaterialLotService lotService;

    @GetMapping
    @PreAuthorize("hasAuthority('STORE_VIEW')")
    public PageResponse<MaterialLotRow> search(@RequestParam Long plantId,
                                               @RequestParam(required = false) Long materialId,
                                               @RequestParam(required = false) Long locationId,
                                               @RequestParam(required = false) Boolean open,
                                               @RequestParam(required = false) String search,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        return lotService.search(plantId, materialId, locationId, open, search, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STORE_VIEW')")
    public MaterialLotDetail get(@PathVariable Long id) {
        return lotService.get(id);
    }

    @GetMapping("/of-document/{documentId}")
    @PreAuthorize("hasAuthority('STORE_VIEW')")
    public List<DocumentLot> forDocument(@PathVariable Long documentId) {
        return lotService.forDocument(documentId);
    }
}
