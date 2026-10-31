package tr.girgin.backend.trading.analysis.platform.domain.analysis.core.service;

import java.util.List;
import org.springframework.stereotype.Service;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.inbound.ValidateExternalAnalysesUseCase;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalAnalysis;
import tr.girgin.backend.trading.analysis.platform.domain.analysis.core.model.ExternalRun;

@Service
class ExternalAnalysisValidationService implements ValidateExternalAnalysesUseCase {

    private final AnalysisTransitionService transitions;

    ExternalAnalysisValidationService(AnalysisTransitionService transitions) {
        this.transitions = transitions;
    }

    @Override
    public void validate(List<ExternalAnalysis> externals) {
        externals.forEach(this::validate);
    }

    private void validate(ExternalAnalysis external) {
        ExternalRun run = external.run();
        if (run == null) {
            if (external.origin() == ExternalAnalysis.Origin.RUN_HISTORY) {
                throw new IllegalArgumentException("A run history record needs its run");
            }
            return;
        }
        if (!transitions.isTerminal(run.status())) {
            throw new IllegalArgumentException("Not a finished run: " + run.status());
        }
        if (run.debateRounds() < 1) {
            throw new IllegalArgumentException("debateRounds must be at least 1: " + run.debateRounds());
        }
    }
}
