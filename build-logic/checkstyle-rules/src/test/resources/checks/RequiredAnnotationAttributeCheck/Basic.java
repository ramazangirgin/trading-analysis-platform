package demo;

import com.example.Marked;
import com.example.Unrelated;

@Marked // violation
class MarkerOnly {}

@Marked(name = "x") // violation
class OtherAttributeOnly {}

@Marked(config = "c")
class WithAttribute {}

@Marked(name = "x", config = "c")
class WithBoth {}

@Marked("x") // violation
class SingleElement {}

@Marked({"a", "b"}) // violation
class ArrayValue {}

@Marked(@Unrelated) // violation
class NestedAnnotation {}

@Marked() // violation
class EmptyParentheses {}

@Unrelated
class NotChecked {}

class Placements {
    @Marked // violation
    void method() {}

    void parameter(@Marked String text) {} // violation
}
