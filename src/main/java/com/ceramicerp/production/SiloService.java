package com.ceramicerp.production;

import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.Machine;
import com.ceramicerp.master.MachineRepository;
import com.ceramicerp.master.MachineType;
import com.ceramicerp.production.dto.SiloStatus;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.DocStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** What each silo holds on record, and "the silo is really empty" when the records say otherwise. */
@Service
@RequiredArgsConstructor
public class SiloService {

    private final SprayRunRepository runRepository;
    private final MachineRepository machineRepository;

    @Transactional(readOnly = true)
    public List<SiloStatus> status(Long plantId) {
        PlantAccess.require(plantId);
        Map<Long, List<SprayRun>> bySilo = runRepository
                .findByPlantIdAndStatusAndPowderLeftGreaterThanOrderByRunStartAsc(plantId, DocStatus.POSTED, BigDecimal.ZERO)
                .stream().collect(Collectors.groupingBy(r -> r.getSilo().getId()));

        List<SiloStatus> result = new ArrayList<>();
        for (Machine silo : machineRepository.findByPlantIdInProcessOrder(plantId)) {
            if (silo.getType() != MachineType.SILO || !silo.isActive()) {
                continue;
            }
            List<SprayRun> runs = bySilo.getOrDefault(silo.getId(), List.of());
            BigDecimal tonnes = runs.stream().map(SprayRun::getPowderLeft).reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new SiloStatus(silo.getId(), silo.getCode(), silo.getName(), tonnes,
                    runs.isEmpty() ? null : madeAt(runs.get(0)),
                    runs.stream().map(r -> new SiloStatus.Run(r.getId(), r.getRunNo(), r.getProdDate(), madeAt(r),
                            r.getPowderLeft(), r.getPowderMoisture())).toList()));
        }
        return result;
    }

    /**
     * The silo was emptied (or the records drifted): set what it holds on record to zero, so the next
     * press entry doesn't "draw" powder that is long gone and break the trace.
     */
    @Transactional
    public List<SiloStatus> markEmpty(Long siloId) {
        Machine silo = machineRepository.findWithPlantById(siloId).orElseThrow(() -> new NotFoundException("Silo", siloId));
        PlantAccess.require(silo.getPlant().getId());
        if (silo.getType() != MachineType.SILO) {
            throw new BusinessException(silo.getCode() + " is not a silo");
        }
        for (SprayRun run : runRepository.findForDraw(siloId)) {
            run.setPowderLeft(BigDecimal.ZERO);
        }
        return status(silo.getPlant().getId());
    }

    /** When the powder was made: the end of the run if known, otherwise its start. */
    private static LocalDateTime madeAt(SprayRun r) {
        return r.getRunEnd() != null ? r.getRunEnd() : r.getRunStart();
    }
}
