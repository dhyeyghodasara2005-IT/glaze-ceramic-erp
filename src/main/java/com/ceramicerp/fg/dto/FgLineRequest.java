package com.ceramicerp.fg.dto;

import com.ceramicerp.fg.BreakageReason;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One row of a warehouse document. The service checks that the fields this type needs are there. */
public record FgLineRequest(
        /** breakage, regrade: the stock row the boxes are taken from */
        Long fromStockId,

        /** opening, regrade: where the boxes go */
        Long pillarId,

        /** opening */
        Long designId,

        /** opening: the batch printed on the boxes */
        @Size(max = 40)
        String batchCode,

        /** opening, regrade: the (new) grade */
        Long gradeId,

        @Size(max = 10)
        String shade,

        @Size(max = 10)
        String caliber,

        @NotNull(message = "Enter the boxes") @Min(1) @Max(1_000_000)
        Integer boxes,

        /** breakage */
        BreakageReason reason,

        @Size(max = 255)
        String remarks
) {
}
