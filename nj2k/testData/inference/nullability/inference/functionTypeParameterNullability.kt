fun <T, E, F, S> foo(x: T, y: List<E>, z: List<F>, a: S) {}

fun bar() {
    val lst: List<Int?> = listOf<Int?>(null)
    val lst2: List<Int> = listOf<Int>(1)
    foo<Int?, Int?, Int, String>(null, lst, lst2, "nya")
}