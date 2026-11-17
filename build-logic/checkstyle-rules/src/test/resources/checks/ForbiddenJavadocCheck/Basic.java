package com.example;

/** A type. */ // violation
public record Basic(
        /** A component. */ String name, // violation
        int size) {

    /** // violation
     * A field, over several lines.
     */
    private static final int LIMIT = 3;

    /** A method. */ // violation
    int work() {
        /** Misplaced, inside a method. */ // violation
        int local = LIMIT;
        return local;
    }

    /** A nested type. */ // violation
    interface Nested {}

    /***/ // violation
    void edge() {}

    // Not Javadoc: an empty block comment, a block comment, a line comment, a string literal.
    /**/
    /* A block comment. */
    /*
     * Another one.
     */
    // A line comment, and not Javadoc.
    void nearMisses() {
        String text = "/** not a comment */";
        String path = "a/**/b";
    }
}
