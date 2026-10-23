package com.ceramicerp.fg;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.fg.dto.FgLedger;
import com.ceramicerp.fg.dto.FgMoveResponse;
import com.ceramicerp.fg.dto.FgStockRow;
import com.ceramicerp.security.PlantAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Reading finished-goods stock: the stock list and one row's ledger. */
@Service
@RequiredArgsConstructor
public class FgStockQueryService {

    /** Design, then batch, then best grade first, then shade, caliber and pillar: how a dispatch clerk reads it. */
    private static final Comparator<FgStockRow> ORDER = Comparator
            .comparing(FgStockRow::designName)
            .thenComparing(FgStockRow::batchCode)
            .thenComparingInt(FgStockRow::gradeSortOrder)
            .thenComparing(FgStockRow::shade)
            .thenComparing(FgStockRow::caliber)
            .thenComparing(FgStockRow::pillarCode, FgPillar.CODE_ORDER);

    private final FgStockRepository stockRepository;
    private final FgMoveRepository moveRepository;

    @Transactional(readOnly = true)
    public List<FgStockRow> search(Long plantId, Long godownId, Long gradeId, String search) {
        PlantAccess.require(plantId);
        String pattern = Text.trimToNull(search) == null ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return stockRepository.search(plantId, godownId, gradeId, pattern).stream()
                .map(FgStockRow::from)
                .sorted(ORDER)
                .toList();
    }

    @Transactional(readOnly = true)
    public FgLedger ledger(Long stockId) {
        FgStock stock = stockRepository.findWithDetailsById(stockId).orElseThrow(() -> new NotFoundException("Stock", stockId));
        PlantAccess.require(stock.getPlant().getId());
        return new FgLedger(FgStockRow.from(stock),
                moveRepository.findByStockIdOrderByIdAsc(stockId).stream().map(FgMoveResponse::from).toList());
    }
}
