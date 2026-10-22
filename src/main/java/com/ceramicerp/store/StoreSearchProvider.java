package com.ceramicerp.store;

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
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Global search for store documents: type "0012" or "GRN/26-27" or the supplier's invoice number.
 * Hit type is "grn", "issue" or "adjustment" so each opens on its own screen.
 */
@Component
@RequiredArgsConstructor
class StoreSearchProvider implements SearchProvider {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final EntityManager em;

    @Override
    public List<SearchHit> search(SearchTerm term, int limit, AppUserDetails user) {
        if (!user.getPermissions().contains(Permission.STORE_VIEW)) {
            return List.of();
        }
        List<StoreDocument> docs = SearchQueries.find(em, """
                select d from StoreDocument d left join fetch d.supplier
                where lower(d.docNo) like :q or lower(d.supplierInvoiceNo) like :q
                order by d.id desc""", StoreDocument.class, term, limit).stream()
                .filter(d -> user.canAccessPlant(d.getPlant().getId()))
                .toList();

        return term.best(docs, limit, StoreDocument::getDocNo, StoreDocument::getSupplierInvoiceNo).stream()
                .map(d -> new SearchHit(d.getDocType().name().toLowerCase(Locale.ROOT), d.getId(), d.getDocNo(),
                        title(d), subtitle(d), d.getStatus() != DocStatus.CANCELLED))
                .toList();
    }

    /** "GRN · Shree Clay Suppliers" / "Material issue" */
    private static String title(StoreDocument d) {
        String kind = switch (d.getDocType()) {
            case GRN -> "GRN";
            case ISSUE -> "Material issue";
            case ADJUSTMENT -> "Stock adjustment";
        };
        return d.getSupplier() == null ? kind : kind + " · " + d.getSupplier().getName();
    }

    /** "22 Oct 2026 · Bill INV-778 · Cancelled" */
    private static String subtitle(StoreDocument d) {
        return Stream.of(d.getDocDate().format(DATE),
                        d.getSupplierInvoiceNo() == null ? null : "Bill " + d.getSupplierInvoiceNo(),
                        d.getStatus() == DocStatus.POSTED ? null : d.getStatus().name().charAt(0)
                                + d.getStatus().name().substring(1).toLowerCase(Locale.ROOT))
                .filter(Objects::nonNull).collect(Collectors.joining(" · "));
    }
}
