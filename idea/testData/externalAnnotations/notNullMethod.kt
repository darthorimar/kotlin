fun test() {
    val x = ClassWithExternalAnnotatedMembers()
    val y: String = x.nullableField

    Integer.valueOf(100)?.and(1)
}