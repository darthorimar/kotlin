class Test {
    fun foo1(r: Function1<Int, String>) {}
    fun foo() {
        foo1 { i: Int ->
            val str: String = ""
            val str2: String = ""

            if (i > 1) {
                return@foo1 str
            }
            str2
        }
    }
}