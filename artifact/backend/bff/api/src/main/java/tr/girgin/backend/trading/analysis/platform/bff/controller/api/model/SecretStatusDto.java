package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

/** A secret's presence. The value itself is never returned: only its last four characters. */
public record SecretStatusDto(String name, String source, String maskedValue) {
}
