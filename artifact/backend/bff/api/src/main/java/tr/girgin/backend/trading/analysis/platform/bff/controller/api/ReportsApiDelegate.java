package tr.girgin.backend.trading.analysis.platform.bff.controller.api;

import tr.girgin.backend.trading.analysis.platform.bff.controller.api.model.ImportResultDto;

public interface ReportsApiDelegate {

    ImportResultDto rescan();
}
