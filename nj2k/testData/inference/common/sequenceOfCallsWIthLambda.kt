fun a(lst: List<String>) {
    val newList: List<Int> = lst
        .asSequence<String>()
        .map<String, Int>({ x: String -> 1 })
        .toList<Int>()
}