fun test() {
    val x: /*T0@*/Int = 1/*LIT*/
    if (x/*T0@Int*/ == null/*LIT*/) {

    }
}

//LOWER <: T0 due to 'INITIALIZER'
//T0 := UPPER due to 'COMPARE_WITH_NULL'
