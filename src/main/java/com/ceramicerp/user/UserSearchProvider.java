package com.ceramicerp.user;

import com.ceramicerp.search.SearchHit;
import com.ceramicerp.search.SearchProvider;
import com.ceramicerp.search.SearchQueries;
import com.ceramicerp.search.SearchTerm;
import com.ceramicerp.security.AppUserDetails;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Global search for users (by name, username or mobile) and roles. */
@Component
@RequiredArgsConstructor
class UserSearchProvider implements SearchProvider {

    private final EntityManager em;

    @Override
    public List<SearchHit> search(SearchTerm term, int limit, AppUserDetails user) {
        List<SearchHit> hits = new ArrayList<>();

        if (user.getPermissions().contains(Permission.USER_VIEW)) {
            List<User> users = SearchQueries.find(em, """
                    select u from User u
                    where lower(u.username) like :q or lower(u.fullName) like :q or u.phone like :q
                    order by u.fullName""", User.class, term, limit);
            term.best(users, limit, User::getUsername, User::getFullName).forEach(u -> hits.add(new SearchHit(
                    "user", u.getId(), u.getUsername(), u.getFullName(),
                    "@" + u.getUsername() + (u.getPhone() == null ? "" : " · " + u.getPhone()), u.isActive())));
        }

        if (user.getPermissions().contains(Permission.ROLE_VIEW)) {
            List<Role> roles = SearchQueries.find(em, """
                    select r from Role r
                    where lower(r.name) like :q
                    order by r.name""", Role.class, term, limit);
            term.best(roles, limit, r -> null, Role::getName).forEach(r -> hits.add(new SearchHit(
                    "role", r.getId(), null, r.getName(), r.getDescription(), true)));
        }
        return hits;
    }
}
