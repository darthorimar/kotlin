import java.util.Arrays.asList

fun foo(args: /*T1@*/Array</*T0@*/Int>) {
    val list: /*T4@*/List</*T3@*/Int> = asList</*T2@*/Int>(*args/*T1@Array<T0@Int>*/)/*MutableList<T2@Int>!!L*/
}

//T1 := LOWER due to 'USE_AS_RECEIVER'
//T1 <: T2 due to 'PARAMETER'
//T3 := T2 due to 'INITIALIZER'
//LOWER <: T4 due to 'INITIALIZER'
