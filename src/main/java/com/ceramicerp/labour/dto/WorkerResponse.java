package com.ceramicerp.labour.dto;

import com.ceramicerp.labour.Department;
import com.ceramicerp.labour.PunchMethod;
import com.ceramicerp.labour.Worker;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WorkerResponse(Long id, Long plantId, String code, String name, ContractorRef contractor,
                             Department department, String job, PunchMethod punch, String deviceId, BigDecimal dayRate,
                             BigDecimal otRate, String phone, LocalDate joinedOn, boolean active, Long version) {

    public record ContractorRef(Long id, String code, String name) {
    }

    public static WorkerResponse from(Worker w) {
        return new WorkerResponse(w.getId(), w.getPlant().getId(), w.getCode(), w.getName(),
                w.getContractor() == null ? null
                        : new ContractorRef(w.getContractor().getId(), w.getContractor().getCode(), w.getContractor().getName()),
                w.getDepartment(), w.getJob(), w.getPunch(), w.getDeviceId(), w.getDayRate(), w.getOtRate(),
                w.getPhone(), w.getJoinedOn(), w.isActive(), w.getVersion());
    }
}
