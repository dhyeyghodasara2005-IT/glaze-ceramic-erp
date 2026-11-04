package com.ceramicerp.production;

import com.ceramicerp.master.MachineRepository;
import com.ceramicerp.master.MachineType;
import com.ceramicerp.production.dto.CurrentShiftResponse;
import com.ceramicerp.production.dto.PressOverview;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.DocStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The press & dryer home screen: each press and what it pressed today, the day's totals, the silos.
 * Only green tiles here - the design is started at the printer (glaze & kiln screen).
 */
@Service
@RequiredArgsConstructor
public class PressService {

    private final MachineRepository machineRepository;
    private final PressEntryRepository entryRepository;
    private final SiloService siloService;
    private final BodyPrepService bodyPrepService;

    @Transactional(readOnly = true)
    public PressOverview overview(Long plantId) {
        PlantAccess.require(plantId);
        CurrentShiftResponse now = bodyPrepService.currentShift();

        long entries = 0;
        long pressed = 0;
        long rejects = 0;
        long good = 0;
        Map<Long, long[]> byPress = new HashMap<>();             // press id -> {entries, pressed, good}
        for (PressEntry e : entryRepository.findByPlantIdAndProdDateAndStatus(plantId, now.prodDate(), DocStatus.POSTED)) {
            entries++;
            pressed += e.getPressedCount();
            rejects += e.getPressRejects() + e.getDryerRejects();
            good += e.goodCount();
            long[] p = byPress.computeIfAbsent(e.getPress().getId(), k -> new long[3]);
            p[0]++;
            p[1] += e.getPressedCount();
            p[2] += e.goodCount();
        }
        List<PressOverview.Line> lines = machineRepository.findByPlantIdInProcessOrder(plantId).stream()
                .filter(m -> m.getType() == MachineType.PRESS && m.isActive())
                .map(m -> {
                    long[] p = byPress.getOrDefault(m.getId(), new long[3]);
                    return new PressOverview.Line(m.getId(), m.getCode(), m.getName(), p[0], p[1], p[2]);
                })
                .toList();
        return new PressOverview(now.prodDate(), now.shiftId(), now.code(), now.name(), lines,
                new PressOverview.Day(entries, pressed, rejects, good), siloService.status(plantId));
    }
}
