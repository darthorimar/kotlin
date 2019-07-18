// ERROR: Calls to static methods in Java interfaces are prohibited in JVM target 1.6. Recompile with '-jvm-target 1.8'
// ERROR: Calls to static methods in Java interfaces are prohibited in JVM target 1.6. Recompile with '-jvm-target 1.8'
import java.util.Comparator

internal class Test {
    fun main(lst: List<Int>) {
        val newLst: List<Int?> = /*before list*/lst/*after list*/.asSequence/*before stream*/()/* after stream*/
                .filter { x: Int -> x > 10 }
                .map { x: Int -> x + 2 }/*some comment*/.distinct/*another comment*/()/* one more comment */.sorted()/*another one comment*/
                .sortedWith(Comparator.naturalOrder())
                .onEach { x: Any? -> println(x) }.take(1)
                .drop(42)/*skipped*/
                /*collecting one*/.toList()/* cool */
    }
}