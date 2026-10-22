package com.ceramicerp.master;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.dto.BodyRecipeRequest;
import com.ceramicerp.master.dto.BodyRecipeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Body recipes: unique codes, at least one material, each material only once. */
@Service
@RequiredArgsConstructor
public class BodyRecipeService {

    private final BodyRecipeRepository recipeRepository;
    private final MaterialRepository materialRepository;

    @Transactional(readOnly = true)
    public List<BodyRecipeResponse> list() {
        return recipeRepository.findAllByOrderByCodeAsc().stream().map(BodyRecipeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<BodyRecipeResponse> listActive() {
        return recipeRepository.findByActiveTrueOrderByCodeAsc().stream().map(BodyRecipeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BodyRecipeResponse get(Long id) {
        return BodyRecipeResponse.from(find(id));
    }

    @Transactional
    public BodyRecipeResponse create(BodyRecipeRequest req) {
        String code = Text.upperOrNull(req.code());
        if (recipeRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Recipe code already exists: " + code);
        }
        BodyRecipe recipe = new BodyRecipe();
        apply(recipe, req, code);
        return BodyRecipeResponse.from(recipeRepository.save(recipe));
    }

    @Transactional
    public BodyRecipeResponse update(Long id, BodyRecipeRequest req) {
        BodyRecipe recipe = find(id);
        String code = Text.upperOrNull(req.code());
        if (recipeRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException("Recipe code already exists: " + code);
        }
        apply(recipe, req, code);
        return BodyRecipeResponse.from(recipe);
    }

    /** Used by production to check the chosen recipe. */
    public BodyRecipe find(Long id) {
        return recipeRepository.findWithLinesById(id).orElseThrow(() -> new NotFoundException("Body recipe", id));
    }

    private void apply(BodyRecipe recipe, BodyRecipeRequest req, String code) {
        recipe.setCode(code);
        recipe.setName(req.name().trim());
        recipe.setDescription(Text.trimToNull(req.description()));
        if (req.active() != null) {
            recipe.setActive(req.active());
        }

        Set<Long> oldMaterials = recipe.getLines().stream().map(l -> l.getMaterial().getId()).collect(Collectors.toSet());
        Map<Long, Material> materials = materialRepository.findAllById(
                        req.lines().stream().map(BodyRecipeRequest.Line::materialId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(BaseEntity::getId, Function.identity()));

        recipe.getLines().clear();
        Set<Long> seen = new HashSet<>();
        int lineNo = 0;
        for (BodyRecipeRequest.Line r : req.lines()) {
            lineNo++;
            Material material = materials.get(r.materialId());
            if (material == null) {
                throw new NotFoundException("Material", r.materialId());
            }
            // Set.add returns false when the value was already there - a quick duplicate check.
            if (!seen.add(material.getId())) {
                throw new BusinessException(material.getCode() + " is in the recipe twice");
            }
            MasterRules.requireActiveIfChanged(material.isActive(),
                    oldMaterials.contains(material.getId()) ? material : null, material, "Material", material.getCode());

            BodyRecipeLine line = new BodyRecipeLine();
            line.setRecipe(recipe);
            line.setLineNo(lineNo);
            line.setMaterial(material);
            line.setQtyPerTon(r.qtyPerTon());
            recipe.getLines().add(line);
        }
    }
}
