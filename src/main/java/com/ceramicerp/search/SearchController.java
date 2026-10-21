package com.ceramicerp.search;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /api/search?q=marble  -> matches across every module the user may see.
 * Any logged-in user can call it; each provider filters by the user's own permissions.
 */
@Tag(name = "Search")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public List<SearchHit> search(@RequestParam(defaultValue = "") String q,
                                  @RequestParam(defaultValue = "5") int limit) {
        return searchService.search(q, limit);
    }
}
