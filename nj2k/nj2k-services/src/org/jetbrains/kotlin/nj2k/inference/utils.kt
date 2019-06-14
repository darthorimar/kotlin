/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.nj2k.inference


inline fun <T, R, S, V> Collection<T>.zip3(other: Collection<R>, another: Collection<S>, transform: (T, R, S) -> V): List<V> {
    val first = iterator()
    val second = other.iterator()
    val third = another.iterator()
    val list = ArrayList<V>(minOf(size, other.size, another.size))
    while (first.hasNext() && second.hasNext() && third.hasNext()) {
        list.add(transform(first.next(), second.next(), third.next()))
    }
    return list
}
