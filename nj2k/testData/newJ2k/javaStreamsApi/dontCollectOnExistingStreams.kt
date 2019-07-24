// ERROR: Calls to static methods in Java interfaces are prohibited in JVM target 1.6. Recompile with '-jvm-target 1.8'
import java.util.stream.Collectors
import java.util.stream.Stream

internal class Test {
    fun main(lst: List<String?>?) {
        val stream: Stream<Int> = Stream.of(1)
        val list: List<Int> = stream.collect(Collectors.toList())
    }
}