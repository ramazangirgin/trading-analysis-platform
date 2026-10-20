package demo;

import com.example.Marked;

@Marked
class ImportedBean {
    static void imported() {} // violation
}

@com.example.Marked
class WrittenQualified {
    static void qualified() {} // violation
}

@org.other.Marked
class WrittenElsewhere {
    static void other() {}
}
