package com.ceramicerp.labour;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.labour.dto.WorkerRequest;
import com.ceramicerp.labour.dto.WorkerResponse;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.party.Party;
import com.ceramicerp.party.PartyRepository;
import com.ceramicerp.party.PartyType;
import com.ceramicerp.security.PlantAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A plant's workers:
 * - code: typed (the number on their card), or the next W-0001 of the plant
 * - the contractor must be a labour contractor (or none: on the plant's own rolls)
 * - a fingerprint-machine number belongs to one worker of the plant
 */
@Service
@RequiredArgsConstructor
public class WorkerService {

    static final String CODE_PREFIX = "W-";

    private final WorkerRepository workerRepository;
    private final PartyRepository partyRepository;
    private final PlantService plantService;

    /** Every filter is optional (null = no filter). */
    @Transactional(readOnly = true)
    public List<WorkerResponse> list(Long plantId, Boolean active, Long contractorId, Department department, String search) {
        PlantAccess.require(plantId);
        String pattern = Text.trimToNull(search) == null ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return workerRepository.search(plantId, active, contractorId, department, pattern).stream()
                .map(WorkerResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkerResponse get(Long id) {
        return WorkerResponse.from(find(id));
    }

    @Transactional
    public WorkerResponse create(WorkerRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        String code = Text.upperOrNull(req.code());
        if (code == null) {
            code = nextCode(plant.getId());
        } else if (workerRepository.existsByPlantIdAndCodeIgnoreCase(plant.getId(), code)) {
            throw new BusinessException("Worker code " + code + " already exists in plant " + plant.getCode());
        }
        Worker w = new Worker();
        w.setPlant(plant);
        w.setCode(code);
        apply(w, req);
        return WorkerResponse.from(workerRepository.save(w));
    }

    @Transactional
    public WorkerResponse update(Long id, WorkerRequest req) {
        Worker w = find(id);
        if (!w.getPlant().getId().equals(req.plantId())) {
            throw new BusinessException("A worker can't be moved to another plant: set them up in the other plant");
        }
        String code = Text.upperOrNull(req.code());
        if (code != null) {
            if (workerRepository.existsByPlantIdAndCodeIgnoreCaseAndIdNot(w.getPlant().getId(), code, id)) {
                throw new BusinessException("Worker code " + code + " already exists in plant " + w.getPlant().getCode());
            }
            w.setCode(code);
        }
        apply(w, req);
        return WorkerResponse.from(w);
    }

    /** Labour contractors for the dropdowns (the active ones). */
    @Transactional(readOnly = true)
    public List<WorkerResponse.ContractorRef> contractors() {
        return partyRepository.findByPartyTypeAndActiveTrueOrderByNameAsc(PartyType.CONTRACTOR).stream()
                .map(p -> new WorkerResponse.ContractorRef(p.getId(), p.getCode(), p.getName()))
                .toList();
    }

    Worker find(Long id) {
        Worker w = workerRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Worker", id));
        PlantAccess.require(w.getPlant().getId());
        return w;
    }

    /** Next free W-0001, W-0002 ... of the plant (the same way parties get DLR-0001). */
    String nextCode(Long plantId) {
        int next = 1;
        for (String existing : workerRepository.findTopCodes(plantId, CODE_PREFIX + "%", Limit.of(20))) {
            String digits = existing.substring(CODE_PREFIX.length());
            if (digits.matches("\\d{1,9}")) {
                next = Integer.parseInt(digits) + 1;
                break;
            }
        }
        String code = CODE_PREFIX + String.format("%04d", next);
        while (workerRepository.existsByPlantIdAndCodeIgnoreCase(plantId, code)) { // someone typed this code by hand
            code = CODE_PREFIX + String.format("%04d", ++next);
        }
        return code;
    }

    private void apply(Worker w, WorkerRequest req) {
        Long plantId = w.getPlant().getId();
        String deviceId = Text.trimToNull(req.deviceId());
        if (deviceId != null && (w.getId() == null
                ? workerRepository.existsByPlantIdAndDeviceIdIgnoreCase(plantId, deviceId)
                : workerRepository.existsByPlantIdAndDeviceIdIgnoreCaseAndIdNot(plantId, deviceId, w.getId()))) {
            throw new BusinessException("Fingerprint number " + deviceId + " is already given to another worker");
        }
        if (req.joinedOn() != null && req.joinedOn().isAfter(LocalDate.now(AttendanceService.PLANT_ZONE))) {
            throw new BusinessException("The joining date can't be in the future");
        }
        w.setName(req.name().trim());
        w.setContractor(contractor(req.contractorId(), w.getContractor()));
        w.setDepartment(req.department());
        w.setJob(Text.trimToNull(req.job()));
        w.setPunch(req.punch());
        w.setDeviceId(deviceId);
        w.setDayRate(req.dayRate());
        w.setOtRate(req.otRate());
        w.setPhone(Text.trimToNull(req.phone()));
        w.setJoinedOn(req.joinedOn());
        if (req.active() != null) {
            w.setActive(req.active());
        }
    }

    /** The worker's contractor: a labour contractor; one that was closed stays only if the worker already had it. */
    private Party contractor(Long id, Party current) {
        if (id == null) {
            return null;
        }
        if (current != null && Objects.equals(current.getId(), id)) {
            return current;
        }
        Party p = partyRepository.findById(id).orElseThrow(() -> new NotFoundException("Party", id));
        if (p.getPartyType() != PartyType.CONTRACTOR) {
            throw new BusinessException(p.getName() + " is a " + p.getPartyType().label() + ", not a labour contractor");
        }
        if (!p.isActive()) {
            throw new BusinessException("Labour contractor " + p.getName() + " is closed");
        }
        return p;
    }
}
