/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.nj2k.inference.nullability

import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.isNullExpression
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.resolve.frontendService
import org.jetbrains.kotlin.nj2k.inference.common.*
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValue
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValueFactory
import org.jetbrains.kotlin.resolve.calls.smartcasts.Nullability
import org.jetbrains.kotlin.resolve.jvm.checkers.mustNotBeNull
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.util.javaslang.getOrNull

class NullabilityBoundTypeEnhancer(private val resolutionFacade: ResolutionFacade) : BoundTypeEnhancer() {
    override fun enhance(
        expression: KtExpression,
        boundType: BoundType,
        inferenceContext: InferenceContext
    ): BoundType {
        return when {
            expression.isNullExpression() ->
                WithForcedStateBoundType(boundType, State.UPPER)
            expression is KtCallExpression -> enhanceCallExpression(expression, boundType, inferenceContext)
            expression is KtQualifiedExpression && expression.selectorExpression is KtCallExpression ->
                enhanceCallExpression(
                    expression.selectorExpression as KtCallExpression,
                    boundType,
                    inferenceContext
                )
            expression is KtLambdaExpression ->
                WithForcedStateBoundType(boundType, State.LOWER)
            else -> boundType
        }
    }

    private fun enhanceCallExpression(
        expression: KtCallExpression,
        boundType: BoundType,
        inferenceContext: InferenceContext
    ): BoundType {
        if (expression.resolveToCall(resolutionFacade)?.candidateDescriptor is ConstructorDescriptor) {
            return WithForcedStateBoundType(boundType, State.LOWER)
        }

        val resolved = expression.calleeExpression?.mainReference?.resolve() ?: return boundType
        if (inferenceContext.isInConversionScope(resolved)) return boundType
        return expression.getExternallyAnnotatedForcedState()?.let { forcedState ->
            WithForcedStateBoundType(boundType, forcedState)
        } ?: boundType
    }

    override fun enhanceKotlinType(type: KotlinType, boundType: BoundType, inferenceContext: InferenceContext): BoundType {
        if (type.arguments.size != boundType.typeParameters.size) return boundType
        val inner = BoundTypeImpl(
            boundType.label,
            boundType.typeParameters.zip(type.arguments) { typeParameter, typeArgument ->
                TypeParameter(
                    enhanceKotlinType(typeArgument.type, typeParameter.boundType, inferenceContext),
                    typeParameter.variance
                )
            }
        )
        return if (type.isMarkedNullable) {
            WithForcedStateBoundType(
                inner,
                State.UPPER
            )
        } else inner
    }

    private fun KtExpression.getExternallyAnnotatedForcedState(): State? {
        val bindingContext = analyze()
        val type = this.getType(bindingContext) ?: return null
        if (!type.isNullable()) return State.LOWER

        val dataFlowValue = resolutionFacade.frontendService<DataFlowValueFactory>()
            .createDataFlowValue(
                this,
                type,
                bindingContext,
                resolutionFacade.moduleDescriptor
            )
        val dataFlowInfo = analyze(resolutionFacade)[BindingContext.EXPRESSION_TYPE_INFO, this]?.dataFlowInfo ?: return null
        return when {
            dataFlowInfo.completeNullabilityInfo.get(dataFlowValue)?.getOrNull() == Nullability.NOT_NULL -> State.LOWER
            type.isExternallyAnnotatedNotNull(dataFlowInfo, dataFlowValue) -> State.LOWER
            else -> null
        }
    }

    private fun KotlinType.isExternallyAnnotatedNotNull(dataFlowInfo: DataFlowInfo, dataFlowValue: DataFlowValue): Boolean =
        mustNotBeNull()?.isFromJava == true && dataFlowInfo.getStableNullability(dataFlowValue).canBeNull()
}