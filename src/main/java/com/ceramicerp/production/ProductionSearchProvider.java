package com.ceramicerp.production;

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

/** Global search for body batch and spray dryer run numbers: type "BB/26-27/0012" or just "0012". */
@Component
@RequiredArgsConstructor
class ProductionSearchProvider implements SearchProvider {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final EntityManager em;

    @Override
    public List<SearchHit> search(SearchTerm term, int limit, AppUserDetails user) {
        if (!user.getPermissions().contains(Permission.PRODUCTION_VIEW)) {
            return List.of();
        }
        List<SearchHit> hits = new ArrayList<>();

        List<BodyBatch> batches = SearchQueries.find(em, """
                select b from BodyBatch b join fetch b.ballMill
                where lower(b.batchNo) like :q
                order by b.id desc""", BodyBatch.class, term, limit).stream()
                .filter(b -> user.canAccessPlant(b.getPlant().getId()))
                .toList();
        term.best(batches, limit, BodyBatch::getBatchNo, BodyBatch::getBatchNo).forEach(b -> hits.add(new SearchHit(
                "body-batch", b.getId(), b.getBatchNo(), "Body batch · " + b.getBallMill().getName(),
                b.getProdDate().format(DATE) + " · " + b.getChargeTon().stripTrailingZeros().toPlainString() + " T · "
                        + b.getStatus().name().toLowerCase(Locale.ROOT),
                b.getStatus() != BodyBatchStatus.CANCELLED)));

        List<SprayRun> runs = SearchQueries.find(em, """
                select r from SprayRun r join fetch r.silo
                where lower(r.runNo) like :q
                order by r.id desc""", SprayRun.class, term, limit).stream()
                .filter(r -> user.canAccessPlant(r.getPlant().getId()))
                .toList();
        term.best(runs, limit, SprayRun::getRunNo, SprayRun::getRunNo).forEach(r -> hits.add(new SearchHit(
                "spray-run", r.getId(), r.getRunNo(), "Spray dryer run · " + r.getSilo().getName(),
                r.getProdDate().format(DATE) + " · " + r.getPowderTon().stripTrailingZeros().toPlainString() + " T powder",
                r.getStatus() != DocStatus.CANCELLED)));
        return hits;
    }
}
