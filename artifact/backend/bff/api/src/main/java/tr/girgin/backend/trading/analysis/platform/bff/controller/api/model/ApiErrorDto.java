package tr.girgin.backend.trading.analysis.platform.bff.controller.api.model;

import java.util.Map;

/** An error for the UI: it shows a translation of {@code errorCode}, filled in from {@code params}. */
public record ApiErrorDto(String errorCode, String message, Map<String, Object> params) {
}
