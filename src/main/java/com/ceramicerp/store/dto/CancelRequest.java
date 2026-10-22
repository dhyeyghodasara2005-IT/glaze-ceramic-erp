package com.ceramicerp.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cancelling a posted document always needs a reason (it shows in the audit trail). */
public record CancelRequest(@NotBlank(message = "Enter the reason") @Size(max = 255) String reason) {
}
