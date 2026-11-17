package checks;

class NotReported {

    // Set the name and age
    void setName() {}

    //
    void emptyComment() {}

    // ---- 42 ----
    void symbolsOnly() {}

    int trailing; // trailing

    /* set name */
    void setNameAfterBlockComment() {}

    /** set name */
    void setNameAfterJavadoc() {}

    void body() {
        // body
    }

    // Why a retry is safe: the call is idempotent.
    void retry() {}

    // the
    void ignoredWordOnly() {}

    // secret
    String text = "secret";
}
// last words
