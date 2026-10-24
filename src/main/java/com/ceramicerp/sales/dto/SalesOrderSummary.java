package com.ceramicerp.sales.dto;

import com.ceramicerp.sales.SalesOrder;
import com.ceramicerp.sales.SalesOrderLine;
import com.ceramicerp.sales.SalesOrderStatus;
import com.ceramicerp.sales.SalesOrderType;

import java.time.LocalDate;

/** One row of the sales order list. */
public record SalesOrderSummary(Long id, String slipNo, LocalDate slipDate, String orderRef, SalesOrderType orderType,
                                SalesOrderStatus status, Long partyId, String partyCode, String partyName,
                                String partyCity, int lines, int boxes, boolean matched, String createdBy) {

    /** Needs the party loaded (the lines are fetched in batches). */
    public static SalesOrderSummary from(SalesOrder o) {
        var party = o.getParty();
        return new SalesOrderSummary(o.getId(), o.getSlipNo(), o.getSlipDate(), o.getOrderRef(), o.getOrderType(),
                o.getStatus(), party.getId(), party.getCode(), party.getName(), party.getCity(), o.getLines().size(),
                o.getLines().stream().mapToInt(SalesOrderLine::getBoxes).sum(),
                o.getLines().stream().allMatch(l -> l.getDesign() != null && l.getGrade() != null),
                o.getCreatedBy());
    }
}
