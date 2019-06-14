/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.nj2k.inference.nullability

import org.jetbrains.kotlin.idea.intentions.branchedTransformations.isNullExpression
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.nj2k.inference.common.*
import org.jetbrains.kotlin.psi.*

class NullabilityConstraintsCollector(resolutionFacade: ResolutionFacade) : ConstraintsCollector(resolutionFacade) {
    override fun ConstraintBuilder.collectAdditionalConstraints(
        expression: KtElement,
        boundTypeCalculator: BoundTypeCalculator,
        inferenceContext: InferenceContext
    ) = with(boundTypeCalculator) {
        when {
            expression is KtBinaryExpression &&
                    (expression.left?.isNullExpression() == true
                            || expression.right?.isNullExpression() == true) -> {
                val notNullOperand =
                    if (expression.left?.isNullExpression() == true) expression.right
                    else expression.left
                notNullOperand?.isTheSameTypeAs(State.UPPER, ConstraintPriority.COMPARE_WITH_NULL)
            }
            expression is KtQualifiedExpression -> {
                expression.receiverExpression.isTheSameTypeAs(State.LOWER, ConstraintPriority.USE_AS_RECEIVER)
            }

            expression is KtForExpression -> {
                expression.loopRange?.isTheSameTypeAs(State.LOWER, ConstraintPriority.USE_AS_RECEIVER)
            }

            expression is KtWhileExpressionBase -> {
                expression.condition?.isTheSameTypeAs(State.LOWER, ConstraintPriority.USE_AS_RECEIVER)
            }

            expression is KtIfExpression -> {
                expression.condition?.isTheSameTypeAs(State.LOWER, ConstraintPriority.USE_AS_RECEIVER)
            }
            expression is KtValueArgument && expression.isSpread -> {
                expression.getArgumentExpression()?.isTheSameTypeAs(State.LOWER, ConstraintPriority.USE_AS_RECEIVER)
            }
            expression is KtBinaryExpression && !KtPsiUtil.isAssignment(expression) -> {
                expression.left?.isTheSameTypeAs(State.LOWER, ConstraintPriority.USE_AS_RECEIVER)
                expression.right?.isTheSameTypeAs(State.LOWER, ConstraintPriority.USE_AS_RECEIVER)
            }
        }
        Unit
    }

}