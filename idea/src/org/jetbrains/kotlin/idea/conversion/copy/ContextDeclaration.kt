/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.conversion.copy

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.types.KotlinType

fun DeclarationDescriptor.asJavaReference(): String? = when (this) {
    is FunctionDescriptor ->
        this.
        """
            
        """.trimIndent()
    else -> null
}

private val KotlinType.rendered: String
    get() = DescriptorRenderer.SHORT_NAMES_IN_TYPES.renderType(this)
