/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.nj2k.inference.common

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

sealed class ClassReference
data class DescriptorClassReference(val descriptor: ClassDescriptor) : ClassReference()
data class TypeParameterReference(val descriptor: TypeParameterDescriptor) : ClassReference()
object NoClassReference : ClassReference()

val ClassDescriptor.classReference: DescriptorClassReference
    get() = DescriptorClassReference(this)

val ClassReference.descriptor: ClassDescriptor?
    get() = safeAs<DescriptorClassReference>()?.descriptor

data class TypeParameter(val boundType: BoundType, val variance: Variance)

sealed class BoundType {
    abstract val label: BoundTypeLabel
    abstract val typeParameters: List<TypeParameter>
}

data class BoundTypeImpl(
    override val label: BoundTypeLabel,
    override val typeParameters: List<TypeParameter>
) : BoundType()

data class WithForcedStateBoundType(
    val original: BoundType,
    val forcedState: State
) : BoundType() {
    override val label: BoundTypeLabel
        get() = original.label
    override val typeParameters: List<TypeParameter>
        get() = original.typeParameters
}

fun BoundType.withEnhancementFrom(from: BoundType) = when (from) {
    is BoundTypeImpl -> this
    is WithForcedStateBoundType ->
        WithForcedStateBoundType(
            this,
            from.forcedState
        )
}


sealed class BoundTypeLabel

data class TypeVariableLabel(val typeVariable: TypeVariable) : BoundTypeLabel()
data class TypeParameterLabel(val typeParameter: TypeParameterDescriptor) : BoundTypeLabel()
data class GenericLabel(val classReference: ClassReference) : BoundTypeLabel()
object NullLiteralLabel : BoundTypeLabel()
object LiteralLabel : BoundTypeLabel()
object StarProjectionlLabel : BoundTypeLabel()


fun TypeVariable.asBoundType(): BoundType =
    BoundTypeImpl(TypeVariableLabel(this), typeParameters)

val BoundType.typeVariable: TypeVariable?
    get() = label.safeAs<TypeVariableLabel>()?.typeVariable

