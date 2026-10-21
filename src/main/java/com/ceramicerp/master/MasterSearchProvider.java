package com.ceramicerp.master;

import com.ceramicerp.search.SearchHit;
import com.ceramicerp.search.SearchProvider;
import com.ceramicerp.search.SearchQueries;
import com.ceramicerp.search.SearchTerm;
import com.ceramicerp.security.AppUserDetails;
import com.ceramicerp.user.Permission;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Global search for product masters: designs, sizes, finishes, categories, grades. */
@Component
@RequiredArgsConstructor
class MasterSearchProvider implements SearchProvider {

    private final EntityManager em;

    @Override
    public List<SearchHit> search(SearchTerm term, int limit, AppUserDetails user) {
        if (!user.getPermissions().contains(Permission.MASTER_VIEW)) {
            return List.of();
        }
        List<SearchHit> hits = new ArrayList<>();

        // join fetch = load the linked category/size/finish in the same query (needed for the subtitle)
        List<Design> designs = SearchQueries.find(em, """
                select d from Design d
                join fetch d.category join fetch d.tileSize join fetch d.finish
                where lower(d.code) like :q or lower(d.name) like :q
                order by d.code""", Design.class, term, limit);
        term.best(designs, limit, Design::getCode, Design::getName).forEach(d -> hits.add(new SearchHit(
                "design", d.getId(), d.getCode(), d.getName(),
                d.getCategory().getCode() + " · " + d.getTileSize().getCode() + " · " + d.getFinish().getName(),
                d.isActive())));

        List<Material> materials = SearchQueries.find(em, """
                select m from Material m join fetch m.group join fetch m.unit
                where lower(m.code) like :q or lower(m.name) like :q
                order by m.code""", Material.class, term, limit);
        term.best(materials, limit, Material::getCode, Material::getName).forEach(m -> hits.add(new SearchHit(
                "material", m.getId(), m.getCode(), m.getName(),
                m.getGroup().getName() + " · " + m.getUnit().getCode(), m.isActive())));

        // Machines belong to a plant: only show those in plants the user may access.
        List<Machine> machines = SearchQueries.find(em, """
                select m from Machine m join fetch m.plant
                where lower(m.code) like :q or lower(m.name) like :q or lower(m.make) like :q
                order by m.code""", Machine.class, term, limit).stream()
                .filter(m -> user.canAccessPlant(m.getPlant().getId()))
                .toList();
        term.best(machines, limit, Machine::getCode, Machine::getName).forEach(m -> hits.add(new SearchHit(
                "machine", m.getId(), m.getCode(), m.getName(), m.getPlant().getName(), m.isActive())));

        List<TileSize> sizes = SearchQueries.find(em, """
                select s from TileSize s
                where lower(s.code) like :q or lower(s.name) like :q
                order by s.code""", TileSize.class, term, limit);
        term.best(sizes, limit, TileSize::getCode, TileSize::getName).forEach(s -> hits.add(new SearchHit(
                "size", s.getId(), s.getCode(), s.getName(),
                s.getSqmPerBox().stripTrailingZeros().toPlainString() + " sq m / box", s.isActive())));

        List<Finish> finishes = SearchQueries.find(em, """
                select f from Finish f
                where lower(f.code) like :q or lower(f.name) like :q
                order by f.name""", Finish.class, term, limit);
        term.best(finishes, limit, Finish::getCode, Finish::getName).forEach(f -> hits.add(new SearchHit(
                "finish", f.getId(), f.getCode(), f.getName(), null, f.isActive())));

        List<ProductCategory> categories = SearchQueries.find(em, """
                select c from ProductCategory c
                where lower(c.code) like :q or lower(c.name) like :q or c.hsnCode like :q
                order by c.name""", ProductCategory.class, term, limit);
        term.best(categories, limit, ProductCategory::getCode, ProductCategory::getName).forEach(c -> hits.add(
                new SearchHit("category", c.getId(), c.getCode(), c.getName(),
                        (c.getHsnCode() == null ? "" : "HSN " + c.getHsnCode() + " · ")
                                + c.getGstRate().stripTrailingZeros().toPlainString() + "% GST",
                        c.isActive())));

        List<Grade> grades = SearchQueries.find(em, """
                select g from Grade g
                where lower(g.code) like :q or lower(g.name) like :q
                order by g.sortOrder""", Grade.class, term, limit);
        term.best(grades, limit, Grade::getCode, Grade::getName).forEach(g -> hits.add(new SearchHit(
                "grade", g.getId(), g.getCode(), g.getName(), g.isSellable() ? null : "Not for sale", g.isActive())));

        return hits;
    }
}
