object TestCharAsInt {
    fun foo(ch: Char) {
        bar('a'.toInt())
        bar(ch.toInt())
    }

    fun bar(i: Int) {}
}