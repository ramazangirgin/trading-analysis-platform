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
        return jdbc.sql("SELECT * FROM presets").query(PresetRow.class).list().stream().map(toPreset::map).toList();
    }

    @Override
    public Optional<Preset> findById(PresetId id) {
        return jdbc.sql("SELECT * FROM presets WHERE id = :id").param("id", id.value())
                .query(PresetRow.class).optional().map(toPreset::map);
    }

    @Override
    public void save(Preset preset) {
        jdbc.sql("""
                INSERT INTO presets (id, name, payload, updated_at) VALUES (:id, :name, :payload, :updatedAt)
                ON CONFLICT (id) DO UPDATE SET name = excluded.name, payload = excluded.payload,
                    updated_at = excluded.updated_at
                """).paramSource(toRow.map(preset)).update();
    }

    @Override
    public boolean delete(PresetId id) {
        return jdbc.sql("DELETE FROM presets WHERE id = :id").param("id", id.value()).update() == 1;
    }
}
