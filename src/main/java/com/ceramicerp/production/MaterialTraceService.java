package com.ceramicerp.production;

import com.ceramicerp.production.dto.MaterialTrace;
import com.ceramicerp.store.DocStatus;
import com.ceramicerp.store.MaterialLot;
import com.ceramicerp.store.MaterialLotDraw;
import com.ceramicerp.store.MaterialLotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The trace between trucks and tiles. The links already exist one by one: a mill charge has its store issue, the issue
 * its lot draws, a spray run its mill charges, a press entry its spray runs. This walks them in either direction.
 */
@Service
@RequiredArgsConstructor
public class MaterialTraceService {

    private final BodyBatchRepository batchRepository;
    private final BodyBatchService batchService;
    private final LotService designLotService;
    private final ProductionLotRepository designLotRepository;
    private final MaterialLotService materialLots;

    /** The trucks behind one mill charge. */
    @Transactional(readOnly = true)
    public List<MaterialTrace.Truck> forBatch(Long batchId) {
        return trucks(List.of(batchService.find(batchId)));
    }

    /** The trucks behind a design lot: its powder, the spray runs that made it, their mill charges. */
    @Transactional(readOnly = true)
    public List<MaterialTrace.Truck> forDesignLot(Long designLotId) {
        designLotService.find(designLotId);
        return trucks(batchRepository.findBehindDesignLot(designLotId, DocStatus.POSTED));
    }

    /** Where one truck went. Only mill charges lead to tiles; other issues show on the lot's own page. */
    @Transactional(readOnly = true)
    public MaterialTrace.Reach forMaterialLot(Long materialLotId) {
        MaterialLot lot = materialLots.find(materialLotId);
        Map<Long, BigDecimal> taken = new LinkedHashMap<>();
        for (MaterialLotDraw d : materialLots.usesOf(lot.getId())) {
            taken.merge(d.getTransaction().getDocument().getId(), d.getQuantity(), BigDecimal::add);
        }
        if (taken.isEmpty()) {
            return new MaterialTrace.Reach(List.of(), List.of());
        }
        List<BodyBatch> batches = batchRepository.findByIssueDocuments(taken.keySet());
        if (batches.isEmpty()) {
            return new MaterialTrace.Reach(List.of(), List.of());
        }
        List<Long> batchIds = batches.stream().map(BodyBatch::getId).toList();
        return new MaterialTrace.Reach(
                batches.stream().map(b -> new MaterialTrace.BatchUse(b.getId(), b.getBatchNo(), b.getProdDate(),
                        taken.get(b.getIssueDocument().getId()))).toList(),
                designLotRepository.findPressedFrom(batchIds, DocStatus.POSTED).stream()
                        .map(l -> new MaterialTrace.DesignLotUse(l.getId(), l.getLotNo(), l.getDesign().getCode(),
                                l.getDesign().getName(), l.getStatus())).toList());
    }

    /** One row per truck, however many mill charges took from it. */
    private List<MaterialTrace.Truck> trucks(Collection<BodyBatch> batches) {
        Map<Long, String> batchOfIssue = batches.stream().filter(b -> b.getIssueDocument() != null)
                .collect(Collectors.toMap(b -> b.getIssueDocument().getId(), BodyBatch::getBatchNo));
        Map<Long, List<MaterialLotDraw>> byLot = new LinkedHashMap<>();
        for (MaterialLotDraw d : materialLots.drawsOf(batchOfIssue.keySet())) {
            byLot.computeIfAbsent(d.getLot().getId(), id -> new ArrayList<>()).add(d);
        }
        return byLot.values().stream().map(draws -> {
            MaterialLot l = draws.get(0).getLot();
            var unit = l.getMaterial().getUnit();
            BigDecimal quantity = draws.stream().map(MaterialLotDraw::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<String> batchNos = draws.stream().map(d -> batchOfIssue.get(d.getTransaction().getDocument().getId()))
                    .distinct().sorted().toList();
            BigDecimal moisture = l.getLine() == null ? null : l.getLine().getMoisturePct();
            BigDecimal dry = moisture == null ? null : quantity.multiply(BigDecimal.valueOf(100).subtract(moisture))
                    .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
            return new MaterialTrace.Truck(l.getId(), l.getLotNo(), l.getMaterial().getCode(), l.getMaterial().getName(),
                    unit.getCode(), unit.getDecimals(), l.getSupplier() == null ? null : l.getSupplier().getName(),
                    l.getDocument() == null ? null : l.getDocument().getVehicleNo(), l.getReceivedOn(), moisture,
                    l.getDocument() == null, quantity, dry, batchNos);
        }).toList();
    }
}
