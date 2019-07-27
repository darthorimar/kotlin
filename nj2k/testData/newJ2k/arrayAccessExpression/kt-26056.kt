class C {
    fun f() {
        val displayMapping = arrayOf("NUL", "ONE"/*, ...*/)
        val chr = '\u0000'
        val toDisplay = displayMapping[chr.toInt()]
        println(toDisplay)
    }
}