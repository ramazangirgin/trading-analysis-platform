package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

/** {@code password} is the new one-time password; blank or missing means "generate one". */
public record ResetPasswordRequest(String password) {}
