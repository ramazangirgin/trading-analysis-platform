package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.PasswordHash;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;

/**
 * One row of the {@code USERS} table and its role assignments in {@code USER_ROLES}. The roles are
 * held by ID, as the domain's {@code User} does: loading a user never loads roles.
 */
@Entity
@Table(name = "USERS")
public class UserEntity {

    @EmbeddedId
    private UserIdEmbeddable id;

    @Convert(converter = UsernameAttributeConverter.class)
    @Column(name = "USERNAME", nullable = false)
    private Username username;

    @Convert(converter = PasswordHashAttributeConverter.class)
    @Column(name = "PASSWORD_HASH", nullable = false)
    private PasswordHash passwordHash;

    @Column(name = "ENABLED", nullable = false)
    private boolean enabled;

    @Column(name = "MUST_CHANGE_PASSWORD", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "FAILED_LOGIN_COUNT", nullable = false)
    private int failedLoginCount;

    @Column(name = "LOCKED_UNTIL")
    private Instant lockedUntil;

    // Not updatable: a save of an existing user keeps the time of the first one.
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "USER_ROLES", joinColumns = @JoinColumn(name = "USER_ID"))
    @Column(name = "ROLE_ID", nullable = false)
    @Convert(converter = RoleIdAttributeConverter.class)
    private Set<RoleId> roleIds = new HashSet<>();

    public UserEntity() {}

    public UserIdEmbeddable getId() {
        return id;
    }

    public void setId(UserIdEmbeddable id) {
        this.id = id;
    }

    public Username getUsername() {
        return username;
    }

    public void setUsername(Username username) {
        this.username = username;
    }

    public PasswordHash getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(PasswordHash passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public void setFailedLoginCount(int failedLoginCount) {
        this.failedLoginCount = failedLoginCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Set<RoleId> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(Set<RoleId> roleIds) {
        this.roleIds = roleIds;
    }
}
