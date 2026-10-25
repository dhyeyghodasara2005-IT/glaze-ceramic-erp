package com.ceramicerp.lab;

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

/** Global search for lab test numbers (LAB/). */
@Component
@RequiredArgsConstructor
class LabSearchProvider implements SearchProvider {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final EntityManager em;

    @Override
    public List<SearchHit> search(SearchTerm term, int limit, AppUserDetails user) {
        var may = user.getPermissions();
        if (!may.contains(Permission.LAB_VIEW) && !may.contains(Permission.LAB_ENTRY)) {
            return List.of();
        }
        List<LabTest> found = SearchQueries.find(em, """
                select t from LabTest t join fetch t.material join fetch t.document d left join fetch d.supplier
                where lower(t.testNo) like :q
                order by t.id desc""", LabTest.class, term, limit).stream()
                .filter(t -> user.canAccessPlant(t.getPlant().getId()))
                .toList();
        return term.best(found, limit, LabTest::getTestNo, t -> t.getMaterial().getName()).stream()
                .map(t -> new SearchHit("lab-test", t.getId(), t.getTestNo(), t.getMaterial().getName(),
                        t.getTestedOn().format(DATE) + (t.getDocument().getSupplier() == null ? ""
                                : " · " + t.getDocument().getSupplier().getName()) + " · " + t.getVerdict().name().toLowerCase(Locale.ROOT),
                        t.getVerdict() == LabVerdict.ACCEPTED))
                .toList();
    }
}
