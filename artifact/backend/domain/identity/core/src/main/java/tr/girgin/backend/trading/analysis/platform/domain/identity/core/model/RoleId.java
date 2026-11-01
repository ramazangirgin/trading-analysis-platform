package tr.girgin.backend.trading.analysis.platform.domain.identity.core.model;

public record RoleId(String value) {

    /** The built-in ADMIN role, seeded with this ID by the identity migration {@code V3__seed_admin_role.sql}. */
    public static final RoleId ADMIN = new RoleId("ADMIN");

    /**
     * The built-in ANALYST role, seeded with this ID by the identity migration
     * {@code V4__seed_analyst_and_viewer_roles.sql}.
     */
    public static final RoleId ANALYST = new RoleId("ANALYST");

    /**
     * The built-in VIEWER role, seeded with this ID by the identity migration
     * {@code V4__seed_analyst_and_viewer_roles.sql}.
     */
    public static final RoleId VIEWER = new RoleId("VIEWER");

    /**
     * The built-in PUBLISHER role, seeded with this ID by the identity migration
     * {@code V10__grant_analysis_share_and_seed_publisher_role.sql}.
     */
    public static final RoleId PUBLISHER = new RoleId("PUBLISHER");
}
