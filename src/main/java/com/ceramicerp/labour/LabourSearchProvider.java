package com.ceramicerp.labour;

import com.ceramicerp.search.SearchHit;
import com.ceramicerp.search.SearchProvider;
import com.ceramicerp.search.SearchQueries;
import com.ceramicerp.search.SearchTerm;
import com.ceramicerp.security.AppUserDetails;
import com.ceramicerp.user.Permission;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Global search for workers: by code, name or fingerprint number. */
@Component
@RequiredArgsConstructor
class LabourSearchProvider implements SearchProvider {

    private final EntityManager em;

    @Override
    public List<SearchHit> search(SearchTerm term, int limit, AppUserDetails user) {
        var may = user.getPermissions();
        if (!may.contains(Permission.LABOUR_VIEW) && !may.contains(Permission.LABOUR_MANAGE)) {
            return List.of();
        }
        List<Worker> found = SearchQueries.find(em, """
                select w from Worker w left join fetch w.contractor
                where lower(w.code) like :q or lower(w.name) like :q or lower(w.deviceId) like :q
                order by w.name""", Worker.class, term, limit).stream()
                .filter(w -> user.canAccessPlant(w.getPlant().getId()))
                .toList();
        return term.best(found, limit, Worker::getCode, Worker::getName).stream()
                .map(w -> new SearchHit("worker", w.getId(), w.getCode(), w.getName(), subtitle(w), w.isActive()))
                .toList();
    }

    /** "Press · operator · Shree Labour Services" */
    private static String subtitle(Worker w) {
        String dept = w.getDepartment().name().charAt(0) + w.getDepartment().name().substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
        return Stream.of(dept, w.getJob(), w.getContractor() == null ? null : w.getContractor().getName())
                .filter(Objects::nonNull).collect(Collectors.joining(" · "));
    }
}
