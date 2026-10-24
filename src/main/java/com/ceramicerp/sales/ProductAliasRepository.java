package com.ceramicerp.sales;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductAliasRepository extends JpaRepository<ProductAlias, Long> {

    @EntityGraph(attributePaths = {"design", "design.tileSize", "grade"})
    List<ProductAlias> findByNameKeyIn(Collection<String> keys);

    Optional<ProductAlias> findByNameKey(String key);
}
