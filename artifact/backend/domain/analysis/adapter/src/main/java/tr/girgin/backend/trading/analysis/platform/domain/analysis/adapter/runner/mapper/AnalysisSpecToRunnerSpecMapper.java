package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.runner.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.runner.spec.RunnerSpec;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisId;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.AnalysisSpec;

@Mapper(uses = {AssetTypeToStringMapper.class, AnalystToStringMapper.class})
public interface AnalysisSpecToRunnerSpecMapper {

    @Mapping(target = "runId", source = "id.value")
    RunnerSpec map(AnalysisId id, AnalysisSpec spec);
}
