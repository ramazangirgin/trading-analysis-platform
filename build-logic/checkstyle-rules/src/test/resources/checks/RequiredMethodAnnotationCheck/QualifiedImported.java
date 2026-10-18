package demo;

import com.example.Required;

class ImportedDelegate {

    @Required
    public void simpleName() {}

    @com.example.Required
    public void qualifiedName() {}

    @org.other.Required
    public void otherQualified() {} // violation

    public void bare() {} // violation
}
