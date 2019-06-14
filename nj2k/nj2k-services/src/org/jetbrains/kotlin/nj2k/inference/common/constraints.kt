/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.nj2k.inference.common

enum class ConstraintPriority {
    SUPER_DECLARATION,
    INITIALIZER,
    COMPARE_WITH_NULL,
    ASSIGNMENT,
    RETURN,
    USE_AS_RECEIVER,
    PARAMETER,
}


sealed class Constraint {
    abstract val priority: ConstraintPriority
}

data class SubtypeConstraint(
    var subtype: ConstraintBound,
    var supertype: ConstraintBound,
    override val priority: ConstraintPriority
) : Constraint()

data class EqualsConstraint(
    var left: ConstraintBound,
    var right: ConstraintBound,
    override val priority: ConstraintPriority
) : Constraint()

fun Constraint.copy() = when (this) {
    is SubtypeConstraint -> copy()
    is EqualsConstraint -> copy()
}

sealed class ConstraintBound
data class TypeVariableBound(val typeVariable: TypeVariable) : ConstraintBound()
data class LiteralBound(val stateLiteral: State) : ConstraintBound()

val TypeVariable.constraintBound: TypeVariableBound
    get() = TypeVariableBound(this)

val State.constraintBound: LiteralBound
    get() = LiteralBound(this)

val BoundTypeLabel.constraintBound: ConstraintBound?
    get() = when (this) {
        is TypeVariableLabel -> typeVariable.constraintBound
        is TypeParameterLabel -> null
        is GenericLabel -> null
        StarProjectionlLabel -> null
        NullLiteralLabel -> LiteralBound(State.UPPER)
        LiteralLabel -> LiteralBound(State.LOWER)
    }

val BoundType.constraintBound: ConstraintBound?
    get() = when (this) {
        is BoundTypeImpl -> label.constraintBound
        is WithForcedStateBoundType -> forcedState.constraintBound
    }