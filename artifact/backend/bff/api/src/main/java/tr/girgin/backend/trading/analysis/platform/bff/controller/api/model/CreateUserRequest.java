package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** {@code password} is the one-time password; blank or missing means "generate one". */
public record CreateUserRequest(
        @NotBlank String username, @NotNull List<String> roleIds, String password) {}
