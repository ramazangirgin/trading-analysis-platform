package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;

/** One row of the {@code ROLES} table; its permissions are an array of the {@code PERMISSION} type on the row. */
@Entity
@Table(name = "ROLES")
public class RoleEntity {

    @EmbeddedId
    private RoleIdEmbeddable id;

    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "BUILT_IN", nullable = false)
    private boolean builtIn;

    @Column(name = "DESCRIPTION", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ARRAY)
    // Hibernate binds the array of an enum as varchar[], which PostgreSQL rejects for an enum array column.
    @ColumnTransformer(write = "cast(? as \"PERMISSION\"[])")
    @Column(name = "PERMISSIONS", nullable = false, columnDefinition = "\"PERMISSION\"[]")
    private Set<Permission> permissions = new HashSet<>();

    public RoleEntity() {}

    public RoleIdEmbeddable getId() {
        return id;
    }

    public void setId(RoleIdEmbeddable id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<Permission> permissions) {
        this.permissions = permissions;
    }
}
