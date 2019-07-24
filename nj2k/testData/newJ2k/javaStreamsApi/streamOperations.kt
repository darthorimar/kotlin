// ERROR: Calls to static methods in Java interfaces are prohibited in JVM target 1.6. Recompile with '-jvm-target 1.8'
// ERROR: Calls to static methods in Java interfaces are prohibited in JVM target 1.6. Recompile with '-jvm-target 1.8'
import java.util.Comparator
import java.util.stream.Collectors

internal class Test {
    fun main(lst: List<Int?>) {
        val newLst: List<Int?>? = lst.stream()
                .filter { x: Int? -> x!! > 10 }
                .map<Int?> { x: Int? -> x!! + 2 }
                .distinct()
                .sorted()
                .sorted(Comparator.naturalOrder())
                .peek { x: Int? -> println(x) }
                .limit(1)
                .skip(42)
                .collect(Collectors.toList())
    }
}