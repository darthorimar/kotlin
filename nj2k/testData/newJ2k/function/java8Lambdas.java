import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;

public class Java8Class {
    public void foo1(Function1<Integer, String> r) {
    }


    public void helper() {
    }

    public void foo() {
        foo1((Integer i) -> {
            helper();
            if (i > 1) {
                return null;
            }

            return "43";
        });
    }
}