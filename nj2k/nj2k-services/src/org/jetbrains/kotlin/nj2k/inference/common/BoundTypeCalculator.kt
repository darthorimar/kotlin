/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.nj2k.inference.common

import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.isNullExpression
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.builtIns
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class BoundTypeCalculator(private val resolutionFacade: ResolutionFacade, private val enhancer: BoundTypeEnhancer) {
    private val cache = mutableMapOf<KtExpression, BoundType>()

    fun expressionsWithBoundType() = cache.toList()

    fun KtExpression.boundType(inferenceContext: InferenceContext): BoundType = cache.getOrPut(this) {
        calculateBoundType(inferenceContext, this)
    }

    private fun calculateBoundType(inferenceContext: InferenceContext, expression: KtExpression): BoundType = when {
        expression.isNullExpression() -> BoundTypeImpl(NullLiteralLabel, emptyList())
        expression is KtParenthesizedExpression -> expression.expression?.boundType(inferenceContext)
        expression is KtConstantExpression
                || expression is KtStringTemplateExpression
                || expression.node?.elementType == KtNodeTypes.BOOLEAN_CONSTANT
                || expression is KtBinaryExpression ->
            BoundTypeImpl(LiteralLabel, emptyList())
        expression is KtQualifiedExpression -> expression.toBoundTypeAsQualifiedExpression(inferenceContext)
        expression is KtBinaryExpressionWithTypeRHS -> expression.toBoundTypeAsCastExpression(inferenceContext)
        expression is KtNameReferenceExpression -> expression.toBoundTypeAsReferenceExpression(inferenceContext)
        expression is KtCallExpression -> expression.toBoundTypeAsCallableExpression(null, inferenceContext)
        expression is KtLambdaExpression -> expression.toBoundTypeAsLambdaExpression(inferenceContext)
        expression is KtLabeledExpression -> expression.baseExpression?.boundType(inferenceContext)
        expression is KtIfExpression -> expression.toBoundTypeAsIfExpression(inferenceContext)
        else -> null
    }?.let { boundType ->
        enhancer.enhance(expression, boundType, inferenceContext)
    } ?: BoundTypeImpl(LiteralLabel, emptyList())
    ?: TODO(expression::class.toString() + "\n" + expression.text)

    private fun KtIfExpression.toBoundTypeAsIfExpression(inferenceContext: InferenceContext): BoundType? {
        val isNullLiteralPossible = then?.isNullExpression() == true || `else`?.isNullExpression() == true
        if (isNullLiteralPossible) {
            return BoundTypeImpl(NullLiteralLabel, emptyList())
        }
        return (then ?: `else`)?.boundType(inferenceContext)//TODO()
    }

    private fun KtLambdaExpression.toBoundTypeAsLambdaExpression(inferenceContext: InferenceContext): BoundType? {
        val descriptor = functionLiteral.resolveToDescriptorIfAny(resolutionFacade).safeAs<FunctionDescriptor>() ?: return null
        val builtIns = getType(analyze())?.builtIns ?: return null
        val prototypeDescriptor = builtIns.getFunction(valueParameters.size)
        val parameterBoundTypes = if (descriptor.valueParameters.size == valueParameters.size) {
            valueParameters.map { parameter ->
                parameter.typeReference?.typeElement?.let { typeElement ->
                    inferenceContext.typeElementToTypeVariable[typeElement]
                }?.let { typeVariable ->
                    typeVariable.asBoundType()
                } ?: return null
            }
        } else {
            descriptor.valueParameters.map { parameter ->
                parameter.type.boundType(null, null, null, false, inferenceContext)
            }
        }
        val returnTypeTypeVariable = inferenceContext.declarationToTypeVariable[functionLiteral] ?: return null
        val returnTypeParameter =
            TypeParameter(
                BoundTypeImpl(
                    TypeVariableLabel(returnTypeTypeVariable),
                    returnTypeTypeVariable.typeParameters
                ),
                Variance.OUT_VARIANCE
            )
        return BoundTypeImpl(
            GenericLabel(prototypeDescriptor.classReference),
            parameterBoundTypes.map { TypeParameter(it, Variance.IN_VARIANCE) } + returnTypeParameter
        )
    }

    private fun KtNameReferenceExpression.toBoundTypeAsReferenceExpression(inferenceContext: InferenceContext): BoundType? =
        mainReference
            .resolve()
            ?.safeAs<KtDeclaration>()
            ?.let {
                inferenceContext.declarationToTypeVariable[it]?.asBoundType()
            }


    private fun KtBinaryExpressionWithTypeRHS.toBoundTypeAsCastExpression(inferenceContext: InferenceContext): BoundType? =
        right?.typeElement
            ?.let { inferenceContext.typeElementToTypeVariable[it]?.asBoundType() }


    private fun KtExpression.toBoundTypeAsCallableExpression(contextBoundType: BoundType?, inferenceContext: InferenceContext): BoundType? {
        val call = getResolvedCall(analyze(resolutionFacade)) ?: return null
        val returnType = call.candidateDescriptor.original.returnType ?: return null
        val callDescriptor = call.candidateDescriptor.original
        val returnTypeVariable = inferenceContext.declarationDescriptorToTypeVariable[callDescriptor]
        val withImplicitContextBoundType = contextBoundType
            ?: call.dispatchReceiver
                ?.type
                ?.boundType(null, null, null, false, inferenceContext)

        return returnType.boundType(
            returnTypeVariable,
            withImplicitContextBoundType,
            call,
            withImplicitContextBoundType != contextBoundType,
            inferenceContext
        )
    }

    private fun KtQualifiedExpression.toBoundTypeAsQualifiedExpression(inferenceContext: InferenceContext): BoundType? {
        val receiverBoundType = receiverExpression.boundType(inferenceContext)
        val selectorExpression = selectorExpression ?: return null
        return selectorExpression.toBoundTypeAsCallableExpression(receiverBoundType, inferenceContext)
    }


    fun KotlinType.boundType(
        typeVariable: TypeVariable?,
        contextBoundType: BoundType?,
        call: ResolvedCall<*>?,
        isImplicitReceiver: Boolean,
        inferenceContext: InferenceContext
    ) = boundTypeUnenhanced(
        typeVariable,
        contextBoundType,
        call,
        isImplicitReceiver,
        inferenceContext
    ).let { boundType ->
        if (!inferenceContext.isInConversionScope(call?.call?.calleeExpression?.mainReference?.resolve() ?: return@let boundType))
            enhancer.enhanceKotlinType(this, boundType, inferenceContext)
        else boundType
    }

    private fun KotlinType.boundTypeUnenhanced(
        typeVariable: TypeVariable?,
        contextBoundType: BoundType?,
        call: ResolvedCall<*>?,
        isImplicitReceiver: Boolean,
        inferenceContext: InferenceContext
    ): BoundType = when (val target = constructor.declarationDescriptor) {
        is ClassDescriptor ->
            BoundTypeImpl(
                typeVariable?.let { TypeVariableLabel(it) } ?: GenericLabel(target.classReference),
                arguments.mapIndexed { i, argument ->
                    TypeParameter(
                        argument.type.boundTypeUnenhanced(
                            typeVariable?.typeParameters?.get(i)?.boundType?.label?.safeAs<TypeVariableLabel>()?.typeVariable,
                            contextBoundType,
                            call,
                            isImplicitReceiver,
                            inferenceContext
                        ),
                        constructor.parameters[i].variance
                    )
                }
            )

        is TypeParameterDescriptor -> {
            val containingDeclaration = target.containingDeclaration
            when {
                containingDeclaration == call?.candidateDescriptor -> {
                    val returnTypeVariable = inferenceContext.typeElementToTypeVariable[
                            call.call.typeArguments[target.index].typeReference?.typeElement
                    ]!!
                    BoundTypeImpl(
                        TypeVariableLabel(returnTypeVariable),
                        returnTypeVariable.typeParameters
                    )
                }
                typeVariable != null && isImplicitReceiver ->
                    BoundTypeImpl(
                        TypeVariableLabel(typeVariable),
                        emptyList()
                    )
                contextBoundType != null ->
                    contextBoundType.typeParameters[target.index].boundType
                containingDeclaration == call?.candidateDescriptor.safeAs<ConstructorDescriptor>()?.constructedClass -> {
                    val returnTypeVariable = inferenceContext.typeElementToTypeVariable[
                            call!!.call.typeArguments[target.index].typeReference?.typeElement
                    ]!!
                    BoundTypeImpl(
                        TypeVariableLabel(returnTypeVariable),
                        returnTypeVariable.typeParameters
                    )
                }
                else -> BoundTypeImpl(
                    TypeParameterLabel(target),
                    emptyList()
                )
            }
        }
        else -> TODO(toString())
    }
}


