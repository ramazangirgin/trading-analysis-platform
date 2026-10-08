package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.entity.PresetIdEmbeddable;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;

/** The entity key of a preset ID, mapped by its {@code value}. */
@Mapper
public interface PresetIdToPresetIdEmbeddableMapper {

    PresetIdEmbeddable map(PresetId source);
}
