package com.ceramicerp.organization;

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
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Global search for companies and plants. Plants are filtered by the user's plant access. */
@Component
@RequiredArgsConstructor
class OrganizationSearchProvider implements SearchProvider {

    private final EntityManager em;

    @Override
    public List<SearchHit> search(SearchTerm term, int limit, AppUserDetails user) {
        List<SearchHit> hits = new ArrayList<>();

        if (user.getPermissions().contains(Permission.COMPANY_VIEW)) {
            List<Company> companies = SearchQueries.find(em, """
                    select c from Company c
                    where lower(c.code) like :q or lower(c.name) like :q or lower(c.gstin) like :q
                    order by c.name""", Company.class, term, limit);
            term.best(companies, limit, Company::getCode, Company::getName).forEach(c -> hits.add(new SearchHit(
                    "company", c.getId(), c.getCode(), c.getName(), join(c.getGstin(), c.getCity()), c.isActive())));
        }

        if (user.getPermissions().contains(Permission.PLANT_VIEW)) {
            List<Plant> plants = SearchQueries.find(em, """
                    select p from Plant p join fetch p.company
                    where lower(p.code) like :q or lower(p.name) like :q or lower(p.city) like :q
                    order by p.name""", Plant.class, term, limit).stream()
                    .filter(p -> user.canAccessPlant(p.getId()))   // never show plants the user can't open
                    .toList();
            term.best(plants, limit, Plant::getCode, Plant::getName).forEach(p -> hits.add(new SearchHit(
                    "plant", p.getId(), p.getCode(), p.getName(), join(p.getCompany().getName(), p.getCity()),
                    p.isActive())));
        }
        return hits;
    }

    /** "A · B", skipping empty parts. */
    private static String join(String... parts) {
        String s = Stream.of(parts).filter(Objects::nonNull).filter(p -> !p.isBlank()).collect(Collectors.joining(" · "));
        return s.isEmpty() ? null : s;
    }
}
