package com.ceramicerp.fg;

import com.ceramicerp.search.SearchHit;
import com.ceramicerp.search.SearchProvider;
import com.ceramicerp.search.SearchQueries;
import com.ceramicerp.search.SearchTerm;
import com.ceramicerp.security.AppUserDetails;
import com.ceramicerp.store.DocStatus;
import com.ceramicerp.user.Permission;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Global search for warehouse document numbers (OS/, BK/, RG/). */
@Component
@RequiredArgsConstructor
class FgSearchProvider implements SearchProvider {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final EntityManager em;

    @Override
    public List<SearchHit> search(SearchTerm term, int limit, AppUserDetails user) {
        if (!user.getPermissions().contains(Permission.FG_VIEW)) {
            return List.of();
        }
        List<FgDocument> docs = SearchQueries.find(em, """
                select d from FgDocument d
                where lower(d.docNo) like :q
                order by d.id desc""", FgDocument.class, term, limit).stream()
                .filter(d -> user.canAccessPlant(d.getPlant().getId()))
                .toList();
        List<SearchHit> hits = new ArrayList<>();
        term.best(docs, limit, FgDocument::getDocNo, FgDocument::getDocNo).forEach(d -> hits.add(new SearchHit(
                "fg-" + d.getDocType().name().toLowerCase(Locale.ROOT), d.getId(), d.getDocNo(),
                switch (d.getDocType()) {
                    case OPENING -> "Opening stock";
                    case BREAKAGE -> "Breakage";
                    case REGRADE -> "Regrading";
                },
                d.getDocDate().format(DATE) + " · " + d.getLines().size() + " rows",
                d.getStatus() != DocStatus.CANCELLED)));
        return hits;
    }
}
