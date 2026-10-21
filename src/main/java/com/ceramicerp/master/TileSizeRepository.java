package com.ceramicerp.master;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Database access for tile sizes. Spring Data writes the SQL from the method names.
 */
public interface TileSizeRepository extends JpaRepository<TileSize, Long> {

    /** All sizes, smallest first (e.g. 300x600 before 600x1200). */
    List<TileSize> findAllByOrderByLengthMmAscWidthMmAsc();

    /** Used when creating: is this code already taken? */
    boolean existsByCodeIgnoreCase(String code);

    /** Used when editing: is this code taken by a DIFFERENT size? */
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}