package com.ceramicerp.party;

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
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Global search for parties: by code, name, GSTIN, mobile or city.
 * Hit type is "dealer", "supplier" or "transporter" so each opens on its own screen.
 */
@Component
@RequiredArgsConstructor
class PartySearchProvider implements SearchProvider {

    private final EntityManager em;

    @Override
    public List<SearchHit> search(SearchTerm term, int limit, AppUserDetails user) {
        if (!user.getPermissions().contains(Permission.PARTY_VIEW)) {
            return List.of();
        }
        List<Party> parties = SearchQueries.find(em, """
                select p from Party p
                where lower(p.code) like :q or lower(p.name) like :q or lower(p.gstin) like :q
                   or p.mobile like :q or lower(p.city) like :q
                order by p.name""", Party.class, term, limit * 3);  // three types share the fetch

        List<SearchHit> hits = new ArrayList<>();
        for (PartyType type : PartyType.values()) {
            List<Party> ofType = parties.stream().filter(p -> p.getPartyType() == type).toList();
            term.best(ofType, limit, Party::getCode, Party::getName).forEach(p -> hits.add(new SearchHit(
                    type.name().toLowerCase(Locale.ROOT), p.getId(), p.getCode(), p.getName(),
                    subtitle(p), p.isActive())));
        }
        return hits;
    }

    /** "Morbi, Gujarat · 24AAACS1234A1Z5" */
    private static String subtitle(Party p) {
        String place = Stream.of(p.getCity(), p.getState() != null ? p.getState() : p.getCountry())
                .filter(Objects::nonNull).collect(Collectors.joining(", "));
        return Stream.of(place.isEmpty() ? null : place, p.getGstin())
                .filter(Objects::nonNull).collect(Collectors.joining(" · "));
    }
}
