package tr.girgin.backend.trading.analysis.platform.domain.settings.core.outbound.persistence;

import java.util.List;
import java.util.Optional;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.Preset;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;

public interface PresetRepositoryPort {

    List<Preset> findAll();

    Optional<Preset> findById(PresetId id);

    void save(Preset preset);

    boolean delete(PresetId id);
}
