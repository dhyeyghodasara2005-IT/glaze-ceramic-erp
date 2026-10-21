package com.ceramicerp.user;

import com.ceramicerp.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashSet;
import java.util.Set;

/** A named bundle of permissions, e.g. "Kiln Supervisor". Created and edited from the UI. */
@Getter
@Setter
@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

    public static final String SUPER_ADMIN = "Super Admin";

    @Column(nullable = false, length = 80)
    private String name;

    private String description;

    /** System roles (Super Admin) cannot be renamed, edited or deleted. */
    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", nullable = false, length = 60)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private Set<Permission> permissions = new HashSet<>();
}
