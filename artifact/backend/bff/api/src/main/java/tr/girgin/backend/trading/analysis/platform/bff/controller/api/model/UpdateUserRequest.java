package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * A null field stays unchanged; at least one of {@code roleIds} and {@code enabled} must be given. {@code version}
 * is optional: the version the client last read, without it the change applies to what is stored.
 */
public record UpdateUserRequest(List<@NotBlank String> roleIds, Boolean enabled, Long version) {}
