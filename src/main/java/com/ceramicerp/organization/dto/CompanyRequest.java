package com.ceramicerp.organization.dto;

import com.ceramicerp.common.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Data the client sends to create or update a company. Validated before reaching the service. */
public record CompanyRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.CODE, message = "2-20 letters, digits, - or _")
        String code,

        @NotBlank @Size(max = 150)
        String name,

        @Size(max = 200)
        String legalName,

        @Pattern(regexp = ValidationPatterns.GSTIN, message = "Invalid GSTIN")
        String gstin,

        @Pattern(regexp = ValidationPatterns.PAN, message = "Invalid PAN")
        String pan,

        @Size(max = 255)
        String addressLine,

        @Size(max = 100)
        String city,

        @Size(max = 100)
        String state,

        @Pattern(regexp = ValidationPatterns.PINCODE, message = "Invalid PIN code")
        String pincode,

        @Pattern(regexp = ValidationPatterns.PHONE, message = "Invalid phone number")
        String phone,

        @Email @Size(max = 150)
        String email,

        /** null = keep current value (true for new companies) */
        Boolean active
) {
}
