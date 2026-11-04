package demo;

import com.example.Marked;
import com.example.Unrelated;

@Marked // violation
class OnType {}

@Marked(name = "x") // violation
class WithAttribute {}

@Unrelated
class NotChecked {}

class Placements {
    @Marked // violation
    void method() {}

    void parameter(@Marked String text) {} // violation

    @Marked // violation
    String field;
}
