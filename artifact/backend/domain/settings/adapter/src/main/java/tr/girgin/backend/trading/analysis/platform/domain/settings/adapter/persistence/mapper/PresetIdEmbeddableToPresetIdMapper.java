package tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.mapper;

import org.mapstruct.Mapper;
import tr.girgin.backend.trading.analysis.platform.domain.settings.adapter.persistence.entity.PresetIdEmbeddable;
import tr.girgin.backend.trading.analysis.platform.domain.settings.core.model.PresetId;

/** A preset ID from its entity key, mapped by its {@code value}. */
@Mapper
public interface PresetIdEmbeddableToPresetIdMapper {

    PresetId map(PresetIdEmbeddable source);
}
