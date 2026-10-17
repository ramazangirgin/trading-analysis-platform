package com.example;

class SamePackageDelegate {

    @Required
    public void samePackage() {}

    public void bare() {} // violation
}
