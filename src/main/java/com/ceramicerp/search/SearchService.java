package com.ceramicerp.search;

import com.ceramicerp.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Asks every SearchProvider for matches and returns them together. */
@Service
@RequiredArgsConstructor
public class SearchService {

    public static final int MAX_QUERY_LENGTH = 100;

    /** Spring injects ALL beans that implement SearchProvider into this list. */
    private final List<SearchProvider> providers;

    @Transactional(readOnly = true)
    public List<SearchHit> search(String q, int limit) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String trimmed = q.trim();
        SearchTerm term = SearchTerm.of(trimmed.length() > MAX_QUERY_LENGTH
                ? trimmed.substring(0, MAX_QUERY_LENGTH)
                : trimmed);
        int perType = Math.min(Math.max(limit, 1), 10);

        List<SearchHit> hits = new ArrayList<>();
        for (SearchProvider provider : providers) {
            hits.addAll(provider.search(term, perType, CurrentUser.get()));
        }
        return hits;
    }
}
