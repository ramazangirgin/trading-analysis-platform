package tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper.StringToRoleIdMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper.UserRowToUserMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.mapper.UserToUserRowMapper;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.row.UserRoleRow;
import tr.girgin.backend.trading.analysis.platform.domain.identity.adapter.persistence.row.UserRow;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.RoleId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.User;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.UserId;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Username;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.outbound.persistence.UserRepositoryPort;

@Component
class JdbcUserRepositoryAdapter implements UserRepositoryPort {

    private final JdbcClient jdbc;
    private final UserToUserRowMapper toRow;
    private final UserRowToUserMapper toUser;
    private final StringToRoleIdMapper toRoleId;

    JdbcUserRepositoryAdapter(
            JdbcClient jdbc, UserToUserRowMapper toRow, UserRowToUserMapper toUser, StringToRoleIdMapper toRoleId) {
        this.jdbc = jdbc;
        this.toRow = toRow;
        this.toUser = toUser;
        this.toRoleId = toRoleId;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return withRoles(jdbc.sql("SELECT * FROM \"USERS\" WHERE \"ID\" = :id")
                        .param("id", id.value())
                        .query(UserRow.class)
                        .list())
                .stream()
                .findFirst();
    }

    @Override
    public Optional<User> findByUsername(Username username) {
        return withRoles(jdbc.sql("SELECT * FROM \"USERS\" WHERE LOWER(\"USERNAME\") = LOWER(:username)")
                        .param("username", username.value())
                        .query(UserRow.class)
                        .list())
                .stream()
                .findFirst();
    }

    @Override
    public List<User> findAll() {
        return withRoles(jdbc.sql("SELECT * FROM \"USERS\" ORDER BY LOWER(\"USERNAME\")")
                .query(UserRow.class)
                .list());
    }

    @Override
    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM \"USERS\"").query(Long.class).single();
    }

    @Override
    @Transactional
    public void save(User user) {
        jdbc.sql("""
                INSERT INTO "USERS" ("ID", "USERNAME", "PASSWORD_HASH", "ENABLED", "MUST_CHANGE_PASSWORD",
                    "FAILED_LOGIN_COUNT", "LOCKED_UNTIL", "CREATED_AT", "UPDATED_AT")
                VALUES (:id, :username, :passwordHash, :enabled, :mustChangePassword, :failedLoginCount,
                    :lockedUntil, :createdAt, :updatedAt)
                ON CONFLICT ("ID") DO UPDATE SET "USERNAME" = EXCLUDED."USERNAME",
                    "PASSWORD_HASH" = EXCLUDED."PASSWORD_HASH", "ENABLED" = EXCLUDED."ENABLED",
                    "MUST_CHANGE_PASSWORD" = EXCLUDED."MUST_CHANGE_PASSWORD",
                    "FAILED_LOGIN_COUNT" = EXCLUDED."FAILED_LOGIN_COUNT", "LOCKED_UNTIL" = EXCLUDED."LOCKED_UNTIL",
                    "UPDATED_AT" = EXCLUDED."UPDATED_AT"
                """).paramSource(toRow.map(user)).update();
        jdbc.sql("DELETE FROM \"USER_ROLES\" WHERE \"USER_ID\" = :userId")
                .param("userId", user.id().value())
                .update();
        for (RoleId roleId : user.roleIds()) {
            jdbc.sql("INSERT INTO \"USER_ROLES\" (\"USER_ID\", \"ROLE_ID\") VALUES (:userId, :roleId)")
                    .param("userId", user.id().value())
                    .param("roleId", roleId.value())
                    .update();
        }
    }

    /** Maps the rows and reads all their role assignments in one query. */
    private List<User> withRoles(List<UserRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> ids = rows.stream().map(UserRow::id).toList();
        Map<String, Set<RoleId>> rolesByUser = roleIdsByUser(ids);
        return rows.stream()
                .map(row -> toUser.map(row).withRoleIds(rolesByUser.getOrDefault(row.id(), Set.of())))
                .toList();
    }

    private Map<String, Set<RoleId>> roleIdsByUser(Collection<String> userIds) {
        Map<String, Set<RoleId>> result = new HashMap<>();
        List<UserRoleRow> assignments = jdbc.sql(
                        "SELECT \"USER_ID\", \"ROLE_ID\" FROM \"USER_ROLES\" WHERE \"USER_ID\" IN (:userIds)")
                .param("userIds", userIds)
                .query(UserRoleRow.class)
                .list();
        for (UserRoleRow assignment : assignments) {
            result.computeIfAbsent(assignment.userId(), _ -> new HashSet<>()).add(toRoleId.map(assignment.roleId()));
        }
        return result;
    }
}
