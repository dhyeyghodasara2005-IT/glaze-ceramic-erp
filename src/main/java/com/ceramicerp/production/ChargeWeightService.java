package com.ceramicerp.production;

import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.Material;
import com.ceramicerp.master.MaterialRepository;
import com.ceramicerp.production.dto.ChargeWeights;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.MaterialLot;
import com.ceramicerp.store.MaterialLotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A recipe is written in dry weight, but what goes on the scale is wet. Clay at 10 % moisture is only 0.9 T of clay per
 * ton, so loading the recipe's weight as it stands puts less clay and more water in the mill than planned.
 *
 *     wet = dry / (1 - moisture / 100)
 *
 * Each lot has its own moisture, so this follows the lots in the order the charge will take them, oldest first.
 * It only works out the weights: nothing is saved or reserved.
 */
@Service
@RequiredArgsConstructor
public class ChargeWeightService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Moisture only means something for what is sold by weight. A litre of water weighs a kilo. */
    private static final Map<String, BigDecimal> LITRES_OF_WATER_PER_UNIT = Map.of(
            "TON", BigDecimal.valueOf(1000),
            "KG", BigDecimal.ONE);

    private final MaterialRepository materialRepository;
    private final MaterialLotService lotService;

    @Transactional(readOnly = true)
    public ChargeWeights.Result work(ChargeWeights.Request req) {
        PlantAccess.require(req.plantId());
        // two lines of one material take from the same lots, so the second starts where the first stopped
        Map<Long, BigDecimal> left = new HashMap<>();
        List<ChargeWeights.LineResult> lines = new ArrayList<>();
        BigDecimal litres = BigDecimal.ZERO;

        for (ChargeWeights.Line line : req.lines()) {
            Material material = materialRepository.findById(line.materialId())
                    .orElseThrow(() -> new NotFoundException("Material", line.materialId()));
            BigDecimal litresPerUnit = LITRES_OF_WATER_PER_UNIT.get(material.getUnit().getCode());
            int decimals = material.getUnit().getDecimals();
            if (litresPerUnit == null) {
                BigDecimal asIs = line.dryQuantity().setScale(decimals, RoundingMode.HALF_UP);
                lines.add(new ChargeWeights.LineResult(line.materialId(), line.locationId(), line.dryQuantity(), asIs,
                        null, false, null, List.of()));
                continue;
            }

            BigDecimal dryNeeded = line.dryQuantity();
            BigDecimal wet = BigDecimal.ZERO;
            BigDecimal water = BigDecimal.ZERO;
            boolean unknown = false;
            boolean anyKnown = false;
            List<ChargeWeights.LotShare> shares = new ArrayList<>();
            for (MaterialLot lot : lotService.openLots(req.plantId(), line.locationId(), line.materialId())) {
                BigDecimal available = left.computeIfAbsent(lot.getId(), id -> lot.getQuantityLeft());
                if (available.signum() == 0) {
                    continue;
                }
                BigDecimal moisture = moistureOf(lot);
                BigDecimal dryShare = moisture == null ? BigDecimal.ONE : HUNDRED.subtract(moisture).divide(HUNDRED, 6, RoundingMode.HALF_UP);
                BigDecimal dryInLot = available.multiply(dryShare);

                boolean enough = dryInLot.compareTo(dryNeeded) >= 0;
                BigDecimal take = enough
                        ? dryNeeded.divide(dryShare, decimals, RoundingMode.HALF_UP).min(available)
                        : available;
                dryNeeded = enough ? BigDecimal.ZERO : dryNeeded.subtract(dryInLot);

                left.put(lot.getId(), available.subtract(take));
                wet = wet.add(take);
                shares.add(new ChargeWeights.LotShare(lot.getLotNo(), moisture, take));
                if (moisture == null) {
                    unknown = true;
                } else {
                    anyKnown = true;
                    water = water.add(take.multiply(moisture).divide(HUNDRED, 6, RoundingMode.HALF_UP));
                }
                if (enough) {
                    break;
                }
            }

            // Not enough in this godown: ask for the rest as it stands. Charging then says what is short.
            BigDecimal shortDry = dryNeeded.signum() > 0 ? dryNeeded.setScale(decimals, RoundingMode.HALF_UP) : null;
            if (shortDry != null) {
                wet = wet.add(shortDry);
            }
            wet = wet.setScale(decimals, RoundingMode.HALF_UP);
            BigDecimal average = anyKnown && wet.signum() > 0 ? water.multiply(HUNDRED).divide(wet, 2, RoundingMode.HALF_UP) : null;
            litres = litres.add(water.multiply(litresPerUnit));
            lines.add(new ChargeWeights.LineResult(line.materialId(), line.locationId(), line.dryQuantity(), wet,
                    average, unknown, shortDry, shares));
        }
        return new ChargeWeights.Result(lines, litres.setScale(0, RoundingMode.HALF_UP));
    }

    /** What the GRN or the lab found. Empty when nothing was recorded (100 % would be a typing slip: no material in it). */
    private static BigDecimal moistureOf(MaterialLot lot) {
        BigDecimal m = lot.getLine() == null ? null : lot.getLine().getMoisturePct();
        return m == null || m.compareTo(HUNDRED) >= 0 ? null : m;
    }
}
