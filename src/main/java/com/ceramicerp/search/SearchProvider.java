package com.ceramicerp.search;

import com.ceramicerp.security.AppUserDetails;

import java.util.List;

/**
 * Plug-in point for the global search. Every module that has something worth finding
 * (masters, parties, invoices, batches ...) adds a @Component implementing this.
 * SearchService finds all of them automatically - nothing else needs to change.
 */
public interface SearchProvider {

    /**
     * Return matches for {@code term}, at most {@code limit} per type.
     * The provider must only return what {@code user} is allowed to see (check permissions / plant access).
     */
    List<SearchHit> search(SearchTerm term, int limit, AppUserDetails user);
}
