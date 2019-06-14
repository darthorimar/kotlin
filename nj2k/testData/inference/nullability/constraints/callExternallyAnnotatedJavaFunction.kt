import java.util.Arrays.asList

fun test() {
    val x: /*T2@*/List</*T1@*/Int> = asList</*T0@*/Int>(1/*LIT*/)/*MutableList<T0@Int>!!L*/
}

//LOWER <: T0 due to 'PARAMETER'
//T1 := T0 due to 'INITIALIZER'
//LOWER <: T2 due to 'INITIALIZER'
