internal class Test {
    fun main(lst: List<String>) {
        val toList = lst.asSequence().toList()
        val toSet = lst.asSequence().toSet()
        val count = lst.asSequence().count().toLong()
        val anyMatch = lst.asSequence().any { v: String -> v.isEmpty() }
        val allMatch = lst.asSequence().all { v: String -> v.isEmpty() }
        val noneMatch = lst.asSequence().none { v: String -> v.isEmpty() }
        lst.asSequence().forEach { v: String -> println(v) }
    }
}