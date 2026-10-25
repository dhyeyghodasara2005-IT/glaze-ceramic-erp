package com.ceramicerp.production;

import com.ceramicerp.master.Machine;
import com.ceramicerp.master.MachineRepository;
import com.ceramicerp.master.MachineType;
import com.ceramicerp.production.dto.CurrentShiftResponse;
import com.ceramicerp.production.dto.LotSummary;
import com.ceramicerp.production.dto.PressOverview;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.DocStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The press & dryer home screen: each line and its design, today's numbers, the silos. */
@Service
@RequiredArgsConstructor
public class PressService {

    private final MachineRepository machineRepository;
    private final ProductionLotRepository lotRepository;
    private final PressEntryRepository entryRepository;
    private final LotService lotService;
    private final SiloService siloService;
    private final BodyPrepService bodyPrepService;

    @Transactional(readOnly = true)
    public PressOverview overview(Long plantId) {
        PlantAccess.require(plantId);
        CurrentShiftResponse now = bodyPrepService.currentShift();

        Map<Long, LotSummary> openByPress = lotService.summaries(lotRepository.findByPlantIdAndStatus(plantId, LotStatus.OPEN))
                .stream().collect(Collectors.toMap(LotSummary::pressId, Function.identity()));
        List<PressOverview.Line> lines = machineRepository.findByPlantIdInProcessOrder(plantId).stream()
                .filter(m -> m.getType() == MachineType.PRESS && m.isActive())
                .map((Machine m) -> new PressOverview.Line(m.getId(), m.getCode(), m.getName(), openByPress.get(m.getId())))
                .toList();

        long entries = 0;
        long pressed = 0;
        long rejects = 0;
        long good = 0;
        BigDecimal goodSqm = BigDecimal.ZERO;
        for (PressEntry e : entryRepository.findByPlantIdAndProdDateAndStatus(plantId, now.prodDate(), DocStatus.POSTED)) {
            entries++;
            pressed += e.getPressedCount();
            rejects += e.getPressRejects() + e.getDryerRejects();
            good += e.goodCount();
            BigDecimal sqm = ProductionRules.sqm(e.goodCount(), ProductionRules.sqmPerTile(e.getLot().getDesign().getTileSize()));
            if (sqm != null) {
                goodSqm = goodSqm.add(sqm);
            }
        }
        return new PressOverview(now.prodDate(), now.shiftId(), now.code(), now.name(), lines,
                new PressOverview.Day(entries, pressed, rejects, good, goodSqm), siloService.status(plantId));
    }
}
