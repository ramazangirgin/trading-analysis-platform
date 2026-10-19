package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record UpdateUserRequest(List<@NotBlank String> roleIds, Boolean enabled, Long version) {}
