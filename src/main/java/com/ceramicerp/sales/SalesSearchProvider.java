package com.ceramicerp.sales;

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

/** Global search for sales orders by loading slip no. or sales order no. */
@Component
@RequiredArgsConstructor
class SalesSearchProvider implements SearchProvider {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final EntityManager em;

    @Override
    public List<SearchHit> search(SearchTerm term, int limit, AppUserDetails user) {
        if (!user.getPermissions().contains(Permission.SALES_VIEW)) {
            return List.of();
        }
        List<SalesOrder> orders = SearchQueries.find(em, """
                select o from SalesOrder o join fetch o.party
                where lower(o.slipNo) like :q or lower(o.orderRef) like :q
                order by o.id desc""", SalesOrder.class, term, limit).stream()
                .filter(o -> user.canAccessPlant(o.getPlant().getId()))
                .toList();
        // the slip no. is the "code"; the sales order no. counts as the "name"
        return term.best(orders, limit, SalesOrder::getSlipNo, o -> o.getOrderRef() == null ? "" : o.getOrderRef())
                .stream()
                .map(o -> new SearchHit("sales-order", o.getId(), o.getSlipNo(), o.getParty().getName(),
                        o.getSlipDate().format(DATE) + (o.getOrderRef() == null ? "" : " · SO " + o.getOrderRef())
                                + " · " + o.getStatus().name().toLowerCase(Locale.ROOT),
                        o.getStatus() != SalesOrderStatus.CANCELLED))
                .toList();
    }
}
