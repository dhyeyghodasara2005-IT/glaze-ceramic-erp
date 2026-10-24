package com.ceramicerp.sales.dto;

import com.ceramicerp.sales.SalesOrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesOrderRequest(
        @NotNull(message = "Select a plant")
        Long plantId,

        @NotBlank(message = "Enter the loading slip no.") @Size(max = 30)
        String slipNo,

        @NotNull(message = "Enter the slip date")
        LocalDate slipDate,

        @Size(max = 30)
        String orderRef,

        LocalDate orderDate,

        @NotNull
        SalesOrderType orderType,

        @NotNull(message = "Choose who the bill goes to")
        Long partyId,

        Long transporterId,

        @Min(1) @Max(1_000_000)
        Integer slipBoxes,

        @DecimalMin("0.1") @Digits(integer = 9, fraction = 1)
        BigDecimal slipWeightKg,

        @Size(max = 500)
        String remarks,

        @NotEmpty(message = "Add at least one product") @Size(max = 100)
        List<@Valid SalesLineRequest> lines
) {
}
