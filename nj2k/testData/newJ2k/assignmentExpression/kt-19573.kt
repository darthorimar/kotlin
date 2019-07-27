class TestAssignmentInArgumentConfusingResolve {
    private var x = 0
    fun setX(xx: Int) {
        notify(xx.also { x = it })
    }

    private fun notify(x: Int) {}
}