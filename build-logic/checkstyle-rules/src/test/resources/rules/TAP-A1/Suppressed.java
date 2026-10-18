package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

class SuppressedApiDelegateImpl {
    // Called only by the framework, never by a route.
    @SuppressWarnings("checkstyle:TAP-A1")
    public String open() {
        return "no permission declared";
    }
}
