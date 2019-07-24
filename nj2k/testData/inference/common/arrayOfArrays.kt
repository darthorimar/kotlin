class Test {
    fun foo() {
        val x: Array<Array<Int>> =
            Array<Array<Int>>(1, {
                arrayOf<Int>(2)
            }
        )
    }
}