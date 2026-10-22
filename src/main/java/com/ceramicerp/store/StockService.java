package com.ceramicerp.store;

import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.master.Material;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The stock engine. The ONLY place that changes stock: it updates the balance and writes the ledger row
 * together, inside the caller's transaction - if anything fails, both are rolled back.
 *
 * Valuation = weighted average per godown:
 *   in  : value = qty x rate given (GRN rate, opening rate)
 *   out : value = qty x current average (balance value / balance qty)
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockBalanceRepository balanceRepository;
    private final StockTransactionRepository transactionRepository;

    /**
     * One stock movement.
     *
     * @param quantity + into stock, - out of stock
     * @param rate     rate for incoming stock; null = use the current average
     * @param value    exact value to use (only when reversing a cancelled document); null = calculate
     */
    public record Movement(StoreLocation location, Material material, BigDecimal quantity, BigDecimal rate,
                           BigDecimal value) {
    }

    /** What the engine actually used - copied back onto issue lines so they show their value. */
    public record Result(BigDecimal rate, BigDecimal value) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Result apply(StoreDocument doc, Movement m, boolean reversal) {
        StockBalance balance = balanceRepository.findForUpdate(m.location().getId(), m.material().getId())
                .orElseGet(() -> newBalance(m));

        BigDecimal qty = m.quantity().setScale(3, RoundingMode.HALF_UP);
        BigDecimal newQty = balance.getQuantity().add(qty);
        if (newQty.signum() < 0) {
            String unit = m.material().getUnit().getCode();
            throw new BusinessException("Not enough " + m.material().getCode() + " in " + m.location().getName()
                    + ": available " + balance.getQuantity().stripTrailingZeros().toPlainString() + " " + unit
                    + ", needed " + qty.negate().stripTrailingZeros().toPlainString() + " " + unit);
        }

        BigDecimal rate;
        BigDecimal value;
        if (m.value() != null) {
            // Reversal: undo exactly what the original movement did.
            value = m.value().setScale(2, RoundingMode.HALF_UP);
            rate = m.rate();
        } else if (qty.signum() > 0) {
            rate = m.rate() != null ? m.rate() : averageRate(balance);
            value = qty.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        } else {
            rate = averageRate(balance);
            // Taking out everything? Take out exactly the whole value, so no paise are left behind by rounding.
            value = newQty.signum() == 0
                    ? balance.getValue().negate()
                    : qty.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        }

        balance.setQuantity(newQty);
        balance.setValue(newQty.signum() == 0 ? BigDecimal.ZERO.setScale(2) : balance.getValue().add(value));

        StockTransaction txn = new StockTransaction();
        txn.setPlant(doc.getPlant());
        txn.setLocation(m.location());
        txn.setMaterial(m.material());
        txn.setTxnDate(doc.getDocDate());
        txn.setQuantity(qty);
        txn.setRate(rate.setScale(4, RoundingMode.HALF_UP));
        txn.setValue(value);
        txn.setBalanceQty(newQty);
        txn.setDocument(doc);
        txn.setDocType(doc.getDocType());
        txn.setDocNo(doc.getDocNo());
        txn.setReversal(reversal);
        transactionRepository.save(txn);

        return new Result(txn.getRate(), value);
    }

    private StockBalance newBalance(Movement m) {
        StockBalance b = new StockBalance();
        b.setPlant(m.location().getPlant());
        b.setLocation(m.location());
        b.setMaterial(m.material());
        return balanceRepository.save(b);
    }

    /** value / quantity, or 0 when the godown is empty. */
    private static BigDecimal averageRate(StockBalance b) {
        if (b.getQuantity().signum() == 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return b.getValue().divide(b.getQuantity(), 4, RoundingMode.HALF_UP);
    }
}
