// ERROR: Calls to static methods in Java interfaces are prohibited in JVM target 1.6. Recompile with '-jvm-target 1.8'
// ERROR: Calls to static methods in Java interfaces are prohibited in JVM target 1.6. Recompile with '-jvm-target 1.8'
// ERROR: Calls to static methods in Java interfaces are prohibited in JVM target 1.6. Recompile with '-jvm-target 1.8'
// ERROR: Calls to static methods in Java interfaces are prohibited in JVM target 1.6. Recompile with '-jvm-target 1.8'
// ERROR: Unresolved reference: @filter
// ERROR: Type mismatch: inferred type is (Mutable)List<Any!>! but List<String?>? was expected
// ERROR: Type mismatch: inferred type is Any! but String? was expected
// ERROR: Type mismatch: inferred type is (Mutable)List<Any!>! but List<String?>? was expected
import java.util.function.Predicate
import java.util.stream.Collectors
import java.util.stream.Stream

internal class Test {
    fun main() {
        val activities: List<String?>? = Stream.of("12")
                .map<Any> { v: String -> v + "nya" }
                .filter { v: Any? -> v != null }
                .flatMap { v: Any ->
                    Stream.of(v)
                            .flatMap { s: Any -> Stream.of(s) }
                }.filter(Predicate<Any> { v: Any ->
                    val name = v.javaClass.name
                    if (name == "name") {
                        return@filter false
                    }
                    name != "other_name"
                })
                .collect(Collectors.toList())
    }
}