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

/**
 * Global search for production numbers (body batches, spray runs, press / glaze / kiln / polish /
 * pack entries, quality checks) and design lots (also by design name).
 */
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

        List<ProductionLot> lots = SearchQueries.find(em, """
                select l from ProductionLot l join fetch l.design join fetch l.press
                where lower(l.lotNo) like :q or lower(l.design.code) like :q or lower(l.design.name) like :q
                order by l.id desc""", ProductionLot.class, term, limit).stream()
                .filter(l -> user.canAccessPlant(l.getPlant().getId()))
                .toList();
        term.best(lots, limit, ProductionLot::getLotNo, l -> l.getDesign().getName()).forEach(l -> hits.add(new SearchHit(
                "lot", l.getId(), l.getLotNo(), "Design lot · " + l.getDesign().getName(),
                l.getPress().getCode() + " · " + l.getOpenedDate().format(DATE) + " · " + l.getStatus().name().toLowerCase(Locale.ROOT),
                l.getStatus() != LotStatus.CANCELLED)));

        List<PressEntry> entries = SearchQueries.find(em, """
                select e from PressEntry e join fetch e.lot l join fetch l.design
                where lower(e.entryNo) like :q
                order by e.id desc""", PressEntry.class, term, limit).stream()
                .filter(e -> user.canAccessPlant(e.getPlant().getId()))
                .toList();
        term.best(entries, limit, PressEntry::getEntryNo, PressEntry::getEntryNo).forEach(e -> hits.add(new SearchHit(
                "press-entry", e.getId(), e.getEntryNo(), "Press entry · " + e.getLot().getDesign().getName(),
                e.getProdDate().format(DATE) + " · " + e.goodCount() + " good tiles",
                e.getStatus() != DocStatus.CANCELLED)));

        List<LineEntry> lineEntries = SearchQueries.find(em, """
                select e from LineEntry e join fetch e.lot l join fetch l.design
                where lower(e.entryNo) like :q
                order by e.id desc""", LineEntry.class, term, limit).stream()
                .filter(e -> user.canAccessPlant(e.getPlant().getId()))
                .toList();
        term.best(lineEntries, limit, LineEntry::getEntryNo, LineEntry::getEntryNo).forEach(e -> hits.add(new SearchHit(
                switch (e.getStage()) {
                    case GLAZE -> "glaze-entry";
                    case KILN -> "kiln-entry";
                    case POLISH -> "polish-entry";
                }, e.getId(), e.getEntryNo(),
                switch (e.getStage()) {
                    case GLAZE -> "Glaze entry · ";
                    case KILN -> "Kiln entry · ";
                    case POLISH -> "Polish entry · ";
                } + e.getLot().getDesign().getName(),
                e.getProdDate().format(DATE) + " · " + e.tilesOut() + " tiles out",
                e.getStatus() != DocStatus.CANCELLED)));

        List<PackEntry> packEntries = SearchQueries.find(em, """
                select e from PackEntry e join fetch e.lot l join fetch l.design
                where lower(e.entryNo) like :q
                order by e.id desc""", PackEntry.class, term, limit).stream()
                .filter(e -> user.canAccessPlant(e.getPlant().getId()))
                .toList();
        term.best(packEntries, limit, PackEntry::getEntryNo, PackEntry::getEntryNo).forEach(e -> hits.add(new SearchHit(
                "pack-entry", e.getId(), e.getEntryNo(), "Pack entry · " + e.getLot().getDesign().getName(),
                e.getProdDate().format(DATE) + " · " + e.getBoxes() + " boxes",
                e.getStatus() != DocStatus.CANCELLED)));

        List<LotCheck> checks = SearchQueries.find(em, """
                select c from LotCheck c join fetch c.lot l join fetch l.design
                where lower(c.checkNo) like :q
                order by c.id desc""", LotCheck.class, term, limit).stream()
                .filter(c -> user.canAccessPlant(c.getPlant().getId()))
                .toList();
        term.best(checks, limit, LotCheck::getCheckNo, LotCheck::getCheckNo).forEach(c -> hits.add(new SearchHit(
                "quality-check", c.getId(), c.getCheckNo(), "Quality check · " + c.getLot().getDesign().getName(),
                c.getCheckDate().format(DATE) + " · " + c.getVerdict().name().toLowerCase(Locale.ROOT),
                c.getStatus() != DocStatus.CANCELLED)));
        return hits;
    }
}
