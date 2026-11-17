package checks;

class Reported {

    int violation;

    // count // violation
    int countViolation;

    // Set the name // violation
    void setNameViolation() {}

    // Set the // violation
    // name
    void setNameViolationOverTwoLines() {}

    // max retry count // violation
    static final int MAX_RETRY_COUNT_VIOLATION = 3;

    // max retry count // violation
    int maxRetryCountViolation;

    // SET NAME // violation
    void setNameViolationInCapitals() {}

    int keyword() {
        // return violation // violation
        return violation;
    }

    // deprecated set name // violation
    @Deprecated
    void setNameViolationAnnotated() {}

    // suppress warnings violation // violation
    @SuppressWarnings("a; b {")
    void suppressWarningsViolation() {}

    // text block violation // violation
    /* note */ String textBlockViolation = """
            a; { b \\ \"""
            """;

    // char violation // violation
    char charViolation = ';';

    // quote violation // violation
    char quoteViolation = '\'';

    // ratio violation // violation
    int ratioViolation = 4 / 2;

    // gap violation // violation

    // an unrelated remark
    void gapViolation() {}

    // body violation // violation
    void bodyViolation() {
        // nothing special
        int ignored;
    }
}
