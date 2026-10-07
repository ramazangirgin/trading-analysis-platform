package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper.PresetRowToPresetMapper;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper.PresetToPresetRowMapper;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.row.PresetRow;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.outbound.persistence.PresetRepositoryPort;

@Component
class JdbcPresetRepositoryAdapter implements PresetRepositoryPort {

    private final JdbcClient jdbc;
    private final PresetToPresetRowMapper toRow;
    private final PresetRowToPresetMapper toPreset;

    JdbcPresetRepositoryAdapter(JdbcClient jdbc, PresetToPresetRowMapper toRow, PresetRowToPresetMapper toPreset) {
        this.jdbc = jdbc;
        this.toRow = toRow;
        this.toPreset = toPreset;
    }

    @Override
    public List<Preset> findAll() {
        return jdbc.sql("SELECT * FROM \"PRESETS\"").query(PresetRow.class).list().stream()
                .map(toPreset::map)
                .toList();
    }

    @Override
    public Optional<Preset> findById(PresetId id) {
        return jdbc.sql("SELECT * FROM \"PRESETS\" WHERE \"ID\" = :id")
                .param("id", id.value())
                .query(PresetRow.class)
                .optional()
                .map(toPreset::map);
    }

    @Override
    public void save(Preset preset) {
        jdbc.sql("""
                INSERT INTO "PRESETS" ("ID", "NAME", "PAYLOAD", "UPDATED_AT")
                VALUES (:id, :name, :payload, :updatedAt)
                ON CONFLICT ("ID") DO UPDATE SET "NAME" = EXCLUDED."NAME", "PAYLOAD" = EXCLUDED."PAYLOAD",
                    "UPDATED_AT" = EXCLUDED."UPDATED_AT"
                """).paramSource(toRow.map(preset)).update();
    }

    @Override
    public boolean delete(PresetId id) {
        return jdbc.sql("DELETE FROM \"PRESETS\" WHERE \"ID\" = :id")
                        .param("id", id.value())
                        .update()
                == 1;
    }
}
