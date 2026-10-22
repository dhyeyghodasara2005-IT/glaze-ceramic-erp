package com.ceramicerp.party.dto;

import com.ceramicerp.common.ValidationPatterns;
import com.ceramicerp.party.GstRegistration;
import com.ceramicerp.party.PartyType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * What the screen sends to create or edit a party. Simple format checks are here;
 * rules that compare fields (GSTIN vs state vs PAN) are in PartyService.
 */
public record PartyRequest(
        @NotNull(message = "Party type is required")
        PartyType partyType,

        /** Optional: leave empty to get the next DLR-/SUP-/TRN- number automatically. */
        @Pattern(regexp = ValidationPatterns.CODE, message = "2-20 letters, digits, - or _")
        String code,

        @NotBlank @Size(max = 150)
        String name,

        @Size(max = 100)
        String contactPerson,

        @NotNull(message = "Select the GST registration type")
        GstRegistration gstRegistration,

        @Pattern(regexp = ValidationPatterns.GSTIN, message = "Invalid GSTIN")
        String gstin,

        @Pattern(regexp = ValidationPatterns.PAN, message = "Invalid PAN")
        String pan,

        @Size(max = 255)
        String addressLine,

        @Size(max = 100)
        String city,

        /** GST state code, e.g. "24". Filled from the GSTIN automatically when there is one. */
        @Pattern(regexp = "^[0-9]{2}$", message = "Invalid state")
        String stateCode,

        @Pattern(regexp = "^[A-Za-z0-9 -]{3,10}$", message = "Invalid PIN / postal code")
        String pincode,

        @Size(max = 60)
        String country,

        @Pattern(regexp = ValidationPatterns.PHONE, message = "Invalid phone number")
        String phone,

        @Pattern(regexp = ValidationPatterns.PHONE, message = "Invalid mobile number")
        String mobile,

        @Email @Size(max = 150)
        String email,

        @Size(max = 60)
        String region,

        @DecimalMin("0") @Digits(integer = 12, fraction = 2)
        BigDecimal creditLimit,

        @Min(0) @Max(365)
        Integer creditDays,

        @Size(max = 100)
        String bankName,

        @Pattern(regexp = "^[0-9A-Za-z]{6,30}$", message = "Invalid account number")
        String bankAccountNo,

        /** 11 characters: 4 letters, 0, then 6 letters/digits, e.g. SBIN0001234 */
        @Pattern(regexp = "^[A-Za-z]{4}0[A-Za-z0-9]{6}$", message = "Invalid IFSC")
        String bankIfsc,

        @Size(max = 500)
        String notes,

        Boolean active
) {
}
