package com.ceramicerp.maintenance;

import com.ceramicerp.search.SearchHit;
import com.ceramicerp.search.SearchProvider;
import com.ceramicerp.search.SearchQueries;
import com.ceramicerp.search.SearchTerm;
import com.ceramicerp.security.AppUserDetails;
import com.ceramicerp.user.Permission;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Global search for breakdown numbers (BD/). */
@Component
@RequiredArgsConstructor
class MaintenanceSearchProvider implements SearchProvider {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private final EntityManager em;

    @Override
    public List<SearchHit> search(SearchTerm term, int limit, AppUserDetails user) {
        var may = user.getPermissions();
        if (!may.contains(Permission.MAINTENANCE_VIEW) && !may.contains(Permission.BREAKDOWN_REPORT)
                && !may.contains(Permission.MAINTENANCE_ENTRY)) {
            return List.of();
        }
        List<Breakdown> found = SearchQueries.find(em, """
                select b from Breakdown b join fetch b.machine
                where lower(b.breakdownNo) like :q
                order by b.id desc""", Breakdown.class, term, limit).stream()
                .filter(b -> user.canAccessPlant(b.getPlant().getId()))
                .toList();
        return term.best(found, limit, Breakdown::getBreakdownNo, b -> b.getMachine().getName()).stream()
                .map(b -> new SearchHit("breakdown", b.getId(), b.getBreakdownNo(), b.getMachine().getName(),
                        b.getStoppedAt().format(WHEN) + " · " + b.getSymptom(), b.getStatus() != BreakdownStatus.CANCELLED))
                .toList();
    }
}
