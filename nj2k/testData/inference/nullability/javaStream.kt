// RUNTIME_WITH_FULL_JDK

import java.util.stream.Stream

fun test(list: List<String>) {
    val x: Stream<String> = list.stream()
        .map<String>({ x: String -> x + "" })

}