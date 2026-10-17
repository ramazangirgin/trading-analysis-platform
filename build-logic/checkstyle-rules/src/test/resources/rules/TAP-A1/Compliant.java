package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.NoPermissionRequired;
import tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.RequiresPermission;
import tr.girgin.backend.trading.analysis.platform.domain.identity.core.model.Permission;

class CompliantApiDelegateImpl {
    @RequiresPermission(Permission.ANALYSIS_RUN)
    public String run() {
        return "needs a permission";
    }

    @NoPermissionRequired("a session is enough")
    public String me() {
        return "open to every logged-in user";
    }

    private String helper() {
        return "not a route";
    }
}
