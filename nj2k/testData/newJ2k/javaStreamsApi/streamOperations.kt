// ERROR: Calls to static methods in Java interfaces are prohibited in JVM target 1.6. Recompile with '-jvm-target 1.8'
// ERROR: Calls to static methods in Java interfaces are prohibited in JVM target 1.6. Recompile with '-jvm-target 1.8'
import java.util.Comparator

internal class Test {
    fun main(lst: List<Int>) {
        val newLst: List<Int?> = lst.asSequence()
                .filter { x: Int -> x > 10 }
                .map { x: Int -> x + 2 }
                .distinct()
                .sorted()
                .sortedWith(Comparator.naturalOrder())
                .onEach { x: Any? -> println(x) }
                .take(1)
                .drop(42)
                .toList()
    }
}