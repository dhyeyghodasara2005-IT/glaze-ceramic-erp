package com.ceramicerp.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    /** Number of active users holding a role - used to protect the last Super Admin. */
    long countByRoles_IdAndActiveTrue(Long roleId);

    long countByRoles_Id(Long roleId);

    /**
     * Direct UPDATE so a login doesn't bump updated_at/updated_by/version
     * (a login is not an "edit" of the user record).
     */
    @Modifying
    @Query("update User u set u.lastLoginAt = :at where u.id = :id")
    void updateLastLoginAt(@Param("id") Long id, @Param("at") Instant at);

    /**
     * Search by username / name / phone. A null parameter means "don't filter on this".
     * {@code search} must already be lower-case and wrapped in %...%.
     */
    @Query("""
            select u from User u
            where (:search is null
                   or lower(u.username) like :search
                   or lower(u.fullName) like :search
                   or u.phone like :search)
              and (:active is null or u.active = :active)
            """)
    Page<User> search(@Param("search") String search, @Param("active") Boolean active, Pageable pageable);
}
