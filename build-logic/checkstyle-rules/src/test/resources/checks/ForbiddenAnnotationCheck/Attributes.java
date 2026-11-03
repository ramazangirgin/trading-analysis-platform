package demo;

import com.example.Marked;
import com.example.Unrelated;

@Marked
class MarkerOnly {}

@Marked(name = "x")
class OtherAttribute {}

@Marked(expression = "a") // violation
class SetsExpression {}

@Marked(name = "x", expression = "a") // violation
class SetsExpressionAmongOthers {}

@Marked("x")
class SingleElement {}

@Marked(@Unrelated)
class NestedAnnotationValue {}

@Marked()
class EmptyParentheses {}

@Unrelated(expression = "a")
class OtherAnnotation {}

class Nested {
    @Marks({
        @Marked(expression = "a"), // violation
        @Marked(name = "x"),
        @Marked(defaultExpression = "b") // violation
    })
    void inside() {}

    void parameter(@Marked(expression = "a") String text) {} // violation
}
