package com.ceramicerp.production.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * What a silo holds on record: the spray runs whose powder is still in it, oldest first.
 *
 * @param oldestAt when the oldest powder in it was made - powder needs a day or two to age before pressing
 */
public record SiloStatus(Long siloId, String code, String name, BigDecimal tonnes, LocalDateTime oldestAt,
                         List<Run> runs) {

    public record Run(Long runId, String runNo, LocalDate prodDate, LocalDateTime madeAt, BigDecimal powderLeft,
                      BigDecimal powderMoisture) {
    }
}
