package com.ceramicerp.sales;

import com.ceramicerp.fg.dto.DesignGradeStock;
import com.ceramicerp.sales.dto.StockKept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * The picks of CONFIRMED orders keep their boxes: what was loaded once ticked, else what was planned.
 * Draft, dispatched and cancelled orders keep nothing (a dispatched order's boxes have left the stock).
 */
public interface SalesOrderPickRepository extends JpaRepository<SalesOrderPick, Long> {

    /** Boxes kept per stock row by confirmed orders other than {@code orderId}. */
    @Query("""
            select new com.ceramicerp.sales.dto.StockKept(p.stock.id, sum(coalesce(p.loadedBoxes, p.plannedBoxes)))
            from SalesOrderPick p
            where p.order.status = com.ceramicerp.sales.SalesOrderStatus.CONFIRMED
              and p.stock.id in :stockIds and p.order.id <> :orderId
            group by p.stock.id
            """)
    List<StockKept> keptByStock(@Param("stockIds") Collection<Long> stockIds, @Param("orderId") Long orderId);

    /** Boxes kept per design and grade in a plant by confirmed orders other than {@code orderId}. */
    @Query("""
            select new com.ceramicerp.fg.dto.DesignGradeStock(p.stock.design.id, p.stock.grade.id,
                   sum(coalesce(p.loadedBoxes, p.plannedBoxes)))
            from SalesOrderPick p
            where p.order.status = com.ceramicerp.sales.SalesOrderStatus.CONFIRMED
              and p.order.plant.id = :plantId and p.stock.design.id in :designIds and p.order.id <> :orderId
            group by p.stock.design.id, p.stock.grade.id
            """)
    List<DesignGradeStock> keptByDesignAndGrade(@Param("plantId") Long plantId,
                                                @Param("designIds") Collection<Long> designIds,
                                                @Param("orderId") Long orderId);
}
