fun a(lst: List<String>) {
    val newList: List<String> = lst
        .asSequence<String>()
        .toList<String>()
}