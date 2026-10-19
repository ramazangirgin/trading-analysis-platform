package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateUserRequest(
        @NotBlank String username, @NotNull List<@NotBlank String> roleIds, String password) {}
