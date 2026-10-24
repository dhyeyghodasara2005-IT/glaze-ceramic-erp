package com.ceramicerp.sales.dto;

import com.ceramicerp.common.ValidationPatterns;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The truck taking the order, noted at loading. Every field may be filled in bit by bit. */
public record TruckRequest(
        /** GJ-36-T-4521, GJ 36 T 4521 or GJ36T4521: kept as GJ36T4521 (the e-way bill's form) */
        @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9 -]*$", message = "Letters and digits only")
        String truckNo,

        @Size(max = 100)
        String driverName,

        @Size(max = 15) @Pattern(regexp = ValidationPatterns.PHONE, message = "Invalid mobile number")
        String driverMobile,

        @Size(max = 30)
        String lrNo
) {
}
