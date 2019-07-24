open class A<T> {
    open fun foo(x: T, y: List<T>) {
    }
}

class B : A<Int>() {
    override fun foo(x: Int, y: List<Int>) {
        super.foo(x, y)
    }
}