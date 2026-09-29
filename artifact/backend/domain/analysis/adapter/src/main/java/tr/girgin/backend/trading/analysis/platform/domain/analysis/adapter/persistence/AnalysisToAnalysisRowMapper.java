package tr.girgin.backend.trading.analysis.platform.domain.analysis.adapter.persistence;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.Analysis;

@Mapper(uses = {InstantToStringMapper.class, AnalystListToStringMapper.class, DurationToMillisMapper.class})
interface AnalysisToAnalysisRowMapper {

    @Mapping(target = "id", source = "id.value")
    @Mapping(target = "ticker", source = "spec.ticker")
    @Mapping(target = "tradeDate", source = "spec.tradeDate")
    @Mapping(target = "assetType", source = "spec.assetType")
    @Mapping(target = "analysts", source = "spec.analysts")
    @Mapping(target = "llmProvider", source = "spec.llmProvider")
    @Mapping(target = "deepThinkLlm", source = "spec.deepThinkLlm")
    @Mapping(target = "quickThinkLlm", source = "spec.quickThinkLlm")
    @Mapping(target = "maxDebateRounds", source = "spec.maxDebateRounds")
    @Mapping(target = "maxRiskDiscussRounds", source = "spec.maxRiskDiscussRounds")
    @Mapping(target = "outputLanguage", source = "spec.outputLanguage")
    @Mapping(target = "checkpointEnabled", source = "spec.checkpointEnabled")
    @Mapping(target = "llmCalls", source = "stats.llmCalls")
    @Mapping(target = "toolCalls", source = "stats.toolCalls")
    @Mapping(target = "tokensIn", source = "stats.tokensIn")
    @Mapping(target = "tokensOut", source = "stats.tokensOut")
    @Mapping(target = "costUsd", source = "stats.costUsd")
    @Mapping(target = "elapsedMs", source = "stats.elapsed")
    AnalysisRow map(Analysis source);
}
