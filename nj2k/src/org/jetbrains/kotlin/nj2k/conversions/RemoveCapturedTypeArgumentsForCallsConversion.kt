/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.isCallOf
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.impl.JKKtLiteralExpressionImpl
import org.jetbrains.kotlin.nj2k.tree.impl.JKTypeArgumentListImpl
import java.math.BigInteger

class RemoveCapturedTypeArgumentsForCallsConversion : RecursiveApplicableConversionBase() {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
//        if (element !is JKMethodCallExpression) return recurse(element)
//        if (element.isCallOf("java.util.stream.Stream.collect")) {
//            element.typeArgumentList = JKTypeArgumentListImpl()
//        }
        return recurse(element)
    }
}
