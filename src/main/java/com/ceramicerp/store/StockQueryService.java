package com.ceramicerp.store;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.master.Material;
import com.ceramicerp.master.MaterialRepository;
import com.ceramicerp.store.dto.LedgerRow;
import com.ceramicerp.store.dto.StockRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only views of stock: "what do we have" and "what happened to it". */
@Service
@RequiredArgsConstructor
public class StockQueryService {

    private final StockBalanceRepository balanceRepository;
    private final StockTransactionRepository transactionRepository;
    private final MaterialRepository materialRepository;

    /**
     * Stock of every material in a plant. Active materials with no stock are included too,
     * so a material that has run out (or never arrived) still shows up as low.
     */
    @Transactional(readOnly = true)
    public List<StockRow> summary(Long plantId) {
        PlantAccess.require(plantId);

        // material id -> its balances in each godown
        Map<Long, List<StockBalance>> byMaterial = new LinkedHashMap<>();
        Map<Long, Material> materials = new LinkedHashMap<>();
        for (Material m : materialRepository.findByActiveTrueOrderByCodeAsc()) {
            materials.put(m.getId(), m);
            byMaterial.put(m.getId(), new ArrayList<>());
        }
        for (StockBalance b : balanceRepository.findByPlantId(plantId)) {
            if (b.getQuantity().signum() == 0 && !b.getMaterial().isActive()) {
                continue;  // retired material with nothing left: don't show it
            }
            materials.putIfAbsent(b.getMaterial().getId(), b.getMaterial());
            byMaterial.computeIfAbsent(b.getMaterial().getId(), id -> new ArrayList<>()).add(b);
        }

        return materials.values().stream()
                .sorted(Comparator.comparing(Material::getCode))
                .map(m -> toRow(m, byMaterial.get(m.getId())))
                .toList();
    }

    private static StockRow toRow(Material m, List<StockBalance> balances) {
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal value = BigDecimal.ZERO;
        List<StockRow.LocationStock> locations = new ArrayList<>();
        for (StockBalance b : balances) {
            qty = qty.add(b.getQuantity());
            value = value.add(b.getValue());
            if (b.getQuantity().signum() != 0) {
                var loc = b.getLocation();
                locations.add(new StockRow.LocationStock(loc.getId(), loc.getCode(), loc.getName(),
                        b.getQuantity(), b.getValue()));
            }
        }
        locations.sort(Comparator.comparing(StockRow.LocationStock::locationCode));
        BigDecimal average = qty.signum() == 0 ? null : value.divide(qty, 4, RoundingMode.HALF_UP);
        boolean low = m.getReorderLevel() != null && qty.compareTo(m.getReorderLevel()) <= 0;
        return new StockRow(m.getId(), m.getCode(), m.getName(), m.getGroup().getName(), m.getUnit().getCode(),
                m.getUnit().getDecimals(), qty, value, average, m.getReorderLevel(), low, m.isActive(), locations);
    }

    /** Movements of one material, newest first. locationId / from / to are optional filters. */
    @Transactional(readOnly = true)
    public PageResponse<LedgerRow> ledger(Long plantId, Long materialId, Long locationId, LocalDate from,
                                          LocalDate to, int page, int size) {
        PlantAccess.require(plantId);
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by(Sort.Order.desc("id")));
        return PageResponse.of(transactionRepository.ledger(plantId, materialId, locationId, from, to, pageable),
                LedgerRow::from);
    }
}
