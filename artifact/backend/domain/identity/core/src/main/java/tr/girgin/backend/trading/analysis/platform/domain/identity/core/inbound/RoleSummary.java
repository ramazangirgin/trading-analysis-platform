package tr.girgin.backend.trading.analysis.platform.domain.identity.core.inbound;

import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Role;

/**
 * A role as an admin sees it.
 *
 * @param userCount how many users, enabled or not, hold the role
 */
public record RoleSummary(Role role, long userCount) {}
