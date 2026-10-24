package com.ceramicerp.fg;

import com.ceramicerp.fg.dto.StockArrival;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FgMoveRepository extends JpaRepository<FgMove, Long> {

    /** A stock row's ledger, oldest first. */
    List<FgMove> findByStockIdOrderByIdAsc(Long stockId);

    /** First and last arrival of boxes per stock row (cancellations and undone dispatches don't count). */
    @Query("""
            select new com.ceramicerp.fg.dto.StockArrival(m.stock.id, min(m.createdAt), max(m.createdAt))
            from FgMove m
            where m.stock.id in :stockIds and m.boxes > 0 and m.reversal = false
            group by m.stock.id
            """)
    List<StockArrival> arrivals(@Param("stockIds") Collection<Long> stockIds);
}
