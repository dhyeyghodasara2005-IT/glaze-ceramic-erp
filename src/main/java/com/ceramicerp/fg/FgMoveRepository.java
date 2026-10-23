package com.ceramicerp.fg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FgMoveRepository extends JpaRepository<FgMove, Long> {

    /** A stock row's ledger, oldest first. */
    List<FgMove> findByStockIdOrderByIdAsc(Long stockId);
}
