package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;

class ViolationApiDelegateImpl {
    public String open() { // violation
        return "anyone logged in";
    }

    @Override
    public String toString() { // violation
        return "no permission declared";
    }
}
