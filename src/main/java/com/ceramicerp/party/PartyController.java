package com.ceramicerp.party;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.party.dto.PartyRequest;
import com.ceramicerp.party.dto.PartyResponse;
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

/**
 * /api/parties?type=DEALER&search=shah&stateCode=08&page=0&size=20
 * One API for dealers, suppliers and transporters; the screen passes the type.
 */
@Tag(name = "Parties")
@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyController {

    private final PartyService partyService;

    @GetMapping
    @PreAuthorize("hasAuthority('PARTY_VIEW')")
    public PageResponse<PartyResponse> search(@RequestParam(required = false) PartyType type,
                                              @RequestParam(required = false) String search,
                                              @RequestParam(required = false) String stateCode,
                                              @RequestParam(required = false) Boolean active,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return partyService.search(type, search, stateCode, active, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PARTY_VIEW')")
    public PartyResponse get(@PathVariable Long id) {
        return partyService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PARTY_MANAGE')")
    public PartyResponse create(@Valid @RequestBody PartyRequest req) {
        return partyService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PARTY_MANAGE')")
    public PartyResponse update(@PathVariable Long id, @Valid @RequestBody PartyRequest req) {
        return partyService.update(id, req);
    }
}
