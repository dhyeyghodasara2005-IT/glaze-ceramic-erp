package com.ceramicerp.labour.dto;

import com.ceramicerp.labour.Department;
import com.ceramicerp.labour.PunchMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param code         empty = the next W-0001 of the plant
 * @param contractorId null = on the plant's own rolls
 * @param deviceId     the worker's number in the fingerprint machine
 */
public record WorkerRequest(
        @NotNull Long plantId,
        @Size(max = 20) @Pattern(regexp = "^$|[A-Za-z0-9_/-]{1,20}", message = "Letters, numbers, - _ / only") String code,
        @NotBlank @Size(max = 100) String name,
        Long contractorId,
        @NotNull Department department,
        @Size(max = 60) String job,
        @NotNull PunchMethod punch,
        @Size(max = 20) String deviceId,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal dayRate,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal otRate,
        @Size(max = 20) String phone,
        LocalDate joinedOn,
        Boolean active) {
}
