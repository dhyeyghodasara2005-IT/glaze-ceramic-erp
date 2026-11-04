package com.ceramicerp.master.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Every machine this one feeds into, as a whole list: the ones left out are unlinked. Empty = feeds nothing. */
public record MachineFeedsRequest(
        @NotNull @Size(max = 20, message = "A machine can feed at most 20 others")
        List<@NotNull Long> toMachineIds
) {
}
