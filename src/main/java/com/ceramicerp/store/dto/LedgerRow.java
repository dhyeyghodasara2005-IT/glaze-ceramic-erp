package com.ceramicerp.store.dto;

import com.ceramicerp.store.StockTransaction;
import com.ceramicerp.store.StoreDocType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One line of a material's stock ledger.
 *
 * @param quantity   + in, - out
 * @param balanceQty stock left in that godown right after this movement
 * @param reversal   true for the reversing row written when a document was cancelled
 */
public record LedgerRow(
        Long id,
        LocalDate txnDate,
        StoreDocType docType,
        String docNo,
        Long documentId,
        String locationCode,
        BigDecimal quantity,
        BigDecimal rate,
        BigDecimal value,
        BigDecimal balanceQty,
        boolean reversal
) {
    public static LedgerRow from(StockTransaction t) {
        return new LedgerRow(t.getId(), t.getTxnDate(), t.getDocType(), t.getDocNo(), t.getDocument().getId(),
                t.getLocation().getCode(), t.getQuantity(), t.getRate(), t.getValue(), t.getBalanceQty(),
                t.isReversal());
    }
}
