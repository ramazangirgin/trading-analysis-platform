package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.row;

import java.time.OffsetDateTime;

public record PresetRow(String id, String name, String payload, OffsetDateTime updatedAt) {}
