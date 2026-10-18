package demo;

import com.example.*;

class OnDemandDelegate {

    @Required
    public void onDemand() {}

    @Other
    public void notRequired() {} // violation
}
