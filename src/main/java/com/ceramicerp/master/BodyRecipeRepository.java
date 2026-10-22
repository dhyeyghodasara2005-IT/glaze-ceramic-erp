package com.ceramicerp.master;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BodyRecipeRepository extends JpaRepository<BodyRecipe, Long> {

    /** Recipes are few and always shown with their lines, so lines come along in the same query. */
    @EntityGraph(attributePaths = {"lines", "lines.material", "lines.material.unit"})
    List<BodyRecipe> findAllByOrderByCodeAsc();

    @EntityGraph(attributePaths = {"lines", "lines.material", "lines.material.unit"})
    List<BodyRecipe> findByActiveTrueOrderByCodeAsc();

    @EntityGraph(attributePaths = {"lines", "lines.material", "lines.material.unit"})
    Optional<BodyRecipe> findWithLinesById(Long id);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
