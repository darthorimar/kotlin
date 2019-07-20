open class A<T> {
    open fun foo(): Map<T, List<T>> {
        TODO()
    }

    open fun bar(): T {
        return null
    }
}

class B : A<Int>() {
    override fun foo(): Map<Int, List<Int>> {
        return null
    }
}