package com.ceramicerp.store;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.dto.DocumentLot;
import com.ceramicerp.store.dto.MaterialLotDetail;
import com.ceramicerp.store.dto.MaterialLotRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which truck is the stock from? StockService calls this with every movement, under the lock it already holds on the
 * godown's balance row, so two people can't draw the same lot at once. The lots of a godown always add up to its
 * stock of that material.
 */
@Service
@RequiredArgsConstructor
public class MaterialLotService {

    private final MaterialLotRepository lotRepository;
    private final MaterialLotDrawRepository drawRepository;
    private final StoreDocumentRepository documentRepository;

    // ------------------------------------------------------------------ with every stock movement

    /** Stock came in: a new lot, numbered after its document and line. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void received(StockTransaction txn, StoreDocumentLine line) {
        StoreDocument doc = txn.getDocument();
        MaterialLot lot = newLot(txn, doc.getDocNo() + "-" + line.getLineNo());
        lot.setSupplier(doc.getSupplier());
        lot.setDocument(doc);
        lot.setLine(line);
        lot.setTransaction(txn);
        lotRepository.save(lot);
    }

    /** Stock went out: taken from the oldest lots first, like powder from a silo. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void draw(StockTransaction txn) {
        BigDecimal needed = txn.getQuantity().negate();
        for (MaterialLot lot : lotRepository.findOpen(txn.getLocation().getId(), txn.getMaterial().getId())) {
            BigDecimal take = needed.min(lot.getQuantityLeft());
            lot.setQuantityLeft(lot.getQuantityLeft().subtract(take));

            MaterialLotDraw draw = new MaterialLotDraw();
            draw.setLot(lot);
            draw.setTransaction(txn);
            draw.setQuantity(take);
            drawRepository.save(draw);

            needed = needed.subtract(take);
            if (needed.signum() == 0) {
                return;
            }
        }
        // StockService has already checked the balance, and the lots add up to it. Failing rolls everything back.
        throw new IllegalStateException("Lots of material " + txn.getMaterial().getCode() + " in godown "
                + txn.getLocation().getCode() + " don't add up to the stock");
    }

    /**
     * A GRN or adjustment is cancelled. A lot that has been used can't be taken back: the mill charges that used it
     * would then point at a truck that never came.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void undoReceipt(StockTransaction original, StockTransaction reversal) {
        MaterialLot lot = lotRepository.findByTransactionId(original.getId()).orElse(null);
        if (lot == null) {
            draw(reversal);  // posted before lots began: its stock is part of the old stock
            return;
        }
        if (lot.getQuantityLeft().compareTo(lot.getQuantityIn()) != 0) {
            String usedBy = drawRepository.findFirstByLotIdAndReversedFalseOrderByIdAsc(lot.getId())
                    .map(d -> d.getTransaction().getDocNo()).orElse("another document");
            throw new BusinessException("Lot " + lot.getLotNo() + " is already used by " + usedBy
                    + ". Cancel that first, or correct the stock with an adjustment.");
        }
        lot.setQuantityLeft(BigDecimal.ZERO);
        lot.setCancelled(true);
    }

    /** An issue is cancelled: what it took goes back to the same lots, so they are used next again. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void undoDraw(StockTransaction original, StockTransaction reversal) {
        List<MaterialLotDraw> draws = drawRepository.findActiveForTransaction(original.getId());
        if (draws.isEmpty()) {
            // posted before lots began: which lot it came from isn't known, so it comes back as old stock
            MaterialLot lot = newLot(reversal, "OLD-T" + reversal.getId());
            lot.setReceivedOn(original.getTxnDate());
            lotRepository.save(lot);
            return;
        }
        for (MaterialLotDraw draw : draws) {
            MaterialLot lot = draw.getLot();
            lot.setQuantityLeft(lot.getQuantityLeft().add(draw.getQuantity()));
            draw.setReversed(true);
        }
    }

    private static MaterialLot newLot(StockTransaction txn, String lotNo) {
        MaterialLot lot = new MaterialLot();
        lot.setPlant(txn.getPlant());
        lot.setMaterial(txn.getMaterial());
        lot.setLocation(txn.getLocation());
        lot.setLotNo(lotNo);
        lot.setReceivedOn(txn.getTxnDate());
        lot.setQuantityIn(txn.getQuantity());
        lot.setQuantityLeft(txn.getQuantity());
        lot.setRate(txn.getRate());
        return lot;
    }

    // ------------------------------------------------------------------ the screens

    /** Lots with stock left show in the order they will be used; the others newest first. */
    @Transactional(readOnly = true)
    public PageResponse<MaterialLotRow> search(Long plantId, Long materialId, Long locationId, Boolean open,
                                               String search, int page, int size) {
        PlantAccess.require(plantId);
        String pattern = Text.trimToNull(search) == null ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        Sort sort = Boolean.TRUE.equals(open)
                ? Sort.by(Sort.Order.asc("material.code"), Sort.Order.asc("receivedOn"), Sort.Order.asc("id"))
                : Sort.by(Sort.Order.desc("receivedOn"), Sort.Order.desc("id"));
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), sort);
        return PageResponse.of(lotRepository.search(plantId, materialId, locationId, open, pattern, pageable),
                MaterialLotRow::from);
    }

    @Transactional(readOnly = true)
    public MaterialLotDetail get(Long id) {
        MaterialLot lot = find(id);
        return MaterialLotDetail.from(lot, drawRepository.findForLot(id));
    }

    /** The lots behind a document: the ones a GRN made, or the ones an issue took from. */
    @Transactional(readOnly = true)
    public List<DocumentLot> forDocument(Long documentId) {
        StoreDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Store document", documentId));
        PlantAccess.require(doc.getPlant().getId());
        List<DocumentLot> rows = new ArrayList<>();
        lotRepository.findMadeBy(documentId).forEach(l -> rows.add(DocumentLot.of(l, l.getQuantityIn())));
        // two lines of one material can take from the same lot: one row for the lot
        Map<Long, BigDecimal> taken = new LinkedHashMap<>();
        Map<Long, MaterialLot> lots = new HashMap<>();
        for (MaterialLotDraw d : drawRepository.findForDocuments(List.of(documentId))) {
            taken.merge(d.getLot().getId(), d.getQuantity(), BigDecimal::add);
            lots.put(d.getLot().getId(), d.getLot());
        }
        taken.forEach((id, quantity) -> rows.add(DocumentLot.of(lots.get(id), quantity)));
        return rows;
    }

    // ------------------------------------------------------------------ for production's trace

    /** Loads a lot and checks plant access. */
    @Transactional(readOnly = true)
    public MaterialLot find(Long id) {
        MaterialLot lot = lotRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Material lot", id));
        PlantAccess.require(lot.getPlant().getId());
        return lot;
    }

    @Transactional(readOnly = true)
    public List<MaterialLotDraw> drawsOf(Collection<Long> documentIds) {
        return documentIds.isEmpty() ? List.of() : drawRepository.findForDocuments(documentIds);
    }

    @Transactional(readOnly = true)
    public List<MaterialLotDraw> usesOf(Long lotId) {
        return drawRepository.findForLot(lotId);
    }
}
