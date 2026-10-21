package com.ceramicerp.search;

import jakarta.persistence.EntityManager;

import java.util.List;

/** Small helper so every SearchProvider runs its query the same way. */
public final class SearchQueries {

    private SearchQueries() {
    }

    /**
     * Runs a JPQL query that uses the parameter {@code :q} (a %...% LIKE pattern).
     * Fetches a few extra rows so SearchTerm.best() can pick the best matches from them.
     */
    public static <T> List<T> find(EntityManager em, String jpql, Class<T> type, SearchTerm term, int limit) {
        return em.createQuery(jpql, type)
                .setParameter("q", term.contains())
                .setMaxResults(limit * 4)
                .getResultList();
    }
}
