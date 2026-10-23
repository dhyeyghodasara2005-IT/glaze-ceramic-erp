package com.ceramicerp.fg;

import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.master.Design;
import com.ceramicerp.master.Grade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * The finished-goods stock engine. The ONLY place that changes fg_stock: it updates the boxes and
 * writes the ledger row together, inside the caller's transaction. Same idea as the store's StockService,
 * but counted in whole boxes and without rupee values (costing comes in step 9).
 */
@Service
@RequiredArgsConstructor
public class FgStockService {

    /** Rows are locked in this order, so two documents touching the same rows never wait for each other in a circle. */
    private static final Comparator<Movement> LOCK_ORDER = Comparator
            .comparing((Movement m) -> m.batch().pillar().getId())
            .thenComparing(m -> m.batch().design().getId())
            .thenComparing(m -> m.batch().batchCode())
            .thenComparing(m -> m.batch().grade().getId())
            .thenComparing(m -> m.batch().shade())
            .thenComparing(m -> m.batch().caliber());

    private final FgStockRepository stockRepository;
    private final FgMoveRepository moveRepository;

    /** What identifies a stock row: which boxes, standing where. */
    public record Batch(FgPillar pillar, Design design, Long lotId, String batchCode, Grade grade, String shade,
                        String caliber) {
    }

    /**
     * One movement.
     *
     * @param boxes + into stock, - out of stock
     */
    public record Movement(Batch batch, int boxes) {
    }

    /** The document that moves the stock, for the ledger. */
    public record Source(FgSource type, Long id, String no, LocalDate date, boolean reversal) {
    }

    /** Applies several movements of one document, in lock order. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyAll(List<Movement> movements, Source source) {
        movements.stream().sorted(LOCK_ORDER).forEach(m -> apply(m, source));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public FgStock apply(Movement m, Source source) {
        Batch b = m.batch();
        FgStock stock = stockRepository.findForUpdate(b.pillar().getId(), b.design().getId(), b.batchCode(),
                        b.grade().getId(), b.shade(), b.caliber())
                .orElseGet(() -> newStock(b));

        int newBoxes = stock.getBoxes() + m.boxes();
        if (newBoxes < 0) {
            throw new BusinessException("Not enough " + b.design().getName() + " " + b.grade().getName() + " shade "
                    + b.shade() + " caliber " + b.caliber() + " (" + b.batchCode() + ") at pillar " + b.pillar().getCode()
                    + ": " + stock.getBoxes() + " boxes there, " + (-m.boxes()) + " needed");
        }
        stock.setBoxes(newBoxes);

        FgMove move = new FgMove();
        move.setPlant(stock.getPlant());
        move.setStock(stock);
        move.setMoveDate(source.date());
        move.setBoxes(m.boxes());
        move.setBalanceBoxes(newBoxes);
        move.setSource(source.type());
        move.setSourceId(source.id());
        move.setSourceNo(source.no());
        move.setReversal(source.reversal());
        moveRepository.save(move);
        return stock;
    }

    private FgStock newStock(Batch b) {
        FgStock s = new FgStock();
        s.setPlant(b.pillar().getGodown().getPlant());
        s.setPillar(b.pillar());
        s.setDesign(b.design());
        s.setLotId(b.lotId());
        s.setBatchCode(b.batchCode());
        s.setGrade(b.grade());
        s.setShade(b.shade());
        s.setCaliber(b.caliber());
        return stockRepository.save(s);
    }
}
