package com.ceramicerp.production;

import com.ceramicerp.master.ShiftService;
import com.ceramicerp.production.dto.BodyBatchSummary;
import com.ceramicerp.production.dto.BodyPrepOverview;
import com.ceramicerp.production.dto.CurrentShiftResponse;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.DocStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;

/** The body preparation home screen: what is in the mills, what is waiting, what was done today. */
@Service
@RequiredArgsConstructor
public class BodyPrepService {

    private final BodyBatchRepository batchRepository;
    private final SprayRunRepository runRepository;
    private final ShiftService shiftService;

    @Transactional(readOnly = true)
    public CurrentShiftResponse currentShift() {
        return shiftService.current(ProductionRules.now())
                .map(c -> new CurrentShiftResponse(c.shift().getId(), c.shift().getCode(), c.shift().getName(), c.prodDate()))
                // No shift covers this time (gaps in the shift timings): still give today's date.
                .orElseGet(() -> new CurrentShiftResponse(null, null, null, ProductionRules.now().toLocalDate()));
    }

    @Transactional(readOnly = true)
    public BodyPrepOverview overview(Long plantId) {
        PlantAccess.require(plantId);
        CurrentShiftResponse now = currentShift();
        LocalDate day = now.prodDate();
        return new BodyPrepOverview(day, now.shiftId(), now.code(), now.name(),
                batchRepository.findByPlantIdAndStatusOrderByMillingStartAsc(plantId, BodyBatchStatus.CHARGED)
                        .stream().map(BodyBatchSummary::from).toList(),
                batchRepository.findByPlantIdAndStatusOrderByMillingStartAsc(plantId, BodyBatchStatus.DISCHARGED)
                        .stream().map(BodyBatchSummary::from).toList(),
                batchRepository.totalForDay(plantId, day,
                        EnumSet.of(BodyBatchStatus.CHARGED, BodyBatchStatus.DISCHARGED, BodyBatchStatus.CONSUMED)),
                runRepository.totalForDay(plantId, day, DocStatus.POSTED));
    }
}
