/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.nj2k.inference.common

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.nj2k.parentOfType
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.asAssignment
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.resolve.bindingContextUtil.getTargetFunction
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.resolve.calls.components.isVararg
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isArrayOfNothing
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

abstract class ConstraintsCollector(private val resolutionFacade: ResolutionFacade) {
    fun collectConstraints(
        boundTypeCalculator: BoundTypeCalculator,
        inferenceContext: InferenceContext,
        elements: List<KtElement>
    ): List<Constraint> {
        val constraintsBuilder = ConstraintBuilder(inferenceContext, boundTypeCalculator)
        for (element in elements) {
            element.forEachDescendantOfType<KtElement> { expression ->
                if (expression.parentOfType<KtImportDirective>() != null) return@forEachDescendantOfType
                with(constraintsBuilder) {
                    collectCommonConstraints(expression, boundTypeCalculator, inferenceContext)
                    collectAdditionalConstraints(expression, boundTypeCalculator, inferenceContext)
                }
            }
        }
        return constraintsBuilder.collectedConstraints
    }

    abstract fun ConstraintBuilder.collectAdditionalConstraints(
        expression: KtElement,
        boundTypeCalculator: BoundTypeCalculator,
        inferenceContext: InferenceContext
    )

    private fun ConstraintBuilder.collectCommonConstraints(
        expression: KtElement,
        boundTypeCalculator: BoundTypeCalculator,
        inferenceContext: InferenceContext
    ) = with(boundTypeCalculator) {
        when {
            expression is KtBinaryExpressionWithTypeRHS && KtPsiUtil.isUnsafeCast(expression) -> {
                expression.right?.typeElement?.let { inferenceContext.typeElementToTypeVariable[it] }?.also { typeVariable ->
                    expression.left.isSubtypeOf(typeVariable, ConstraintPriority.ASSIGNMENT)
                }
            }

            expression is KtBinaryExpression && expression.asAssignment() != null -> {
                expression.right?.isSubtypeOf(expression.left ?: return, ConstraintPriority.ASSIGNMENT)
            }


            expression is KtVariableDeclaration -> {
                inferenceContext.declarationToTypeVariable[expression]?.also { typeVariable ->
                    expression.initializer?.isSubtypeOf(typeVariable, ConstraintPriority.INITIALIZER)
                }
            }

            expression is KtParameter -> {
                inferenceContext.declarationToTypeVariable[expression]?.also { typeVariable ->
                    expression.defaultValue?.isSubtypeOf(
                        typeVariable,
                        ConstraintPriority.INITIALIZER
                    )
                }
            }


            expression is KtReturnExpression -> {
                val functionTypeVariable = expression.getTargetFunction(expression.analyze(resolutionFacade))
                    ?.resolveToDescriptorIfAny(resolutionFacade)
                    ?.let { functionDescriptor ->
                        inferenceContext.declarationDescriptorToTypeVariable[functionDescriptor]
                    } ?: return
                expression.returnedExpression?.isSubtypeOf(
                    functionTypeVariable,
                    ConstraintPriority.RETURN
                )
            }

            expression is KtReturnExpression -> {
                val targetTypeVariable = expression.getTargetFunction(expression.analyze(resolutionFacade))?.let { function ->
                    inferenceContext.declarationToTypeVariable[function]
                }
                if (targetTypeVariable != null) {
                    expression.returnedExpression?.isSubtypeOf(
                        targetTypeVariable,
                        ConstraintPriority.RETURN
                    )
                }
            }

            expression is KtLambdaExpression -> {
                val targetTypeVariable =
                    inferenceContext.declarationToTypeVariable[expression.functionLiteral] ?: return
                expression.functionLiteral.bodyExpression?.statements?.lastOrNull()
                    ?.takeIf { it !is KtReturnExpression }
                    ?.also { implicitReturn ->
                        implicitReturn.isSubtypeOf(
                            targetTypeVariable,
                            ConstraintPriority.RETURN
                        )
                    }
            }

            expression is KtCallElement -> {
                val call = expression.resolveToCall(resolutionFacade) ?: return
                if (call.candidateDescriptor !is FunctionDescriptor) return
                val valueArguments = call.valueArgumentsByIndex.orEmpty()
                val typeParameterBindings =
                    call.candidateDescriptor.typeParameters.zip(call.call.typeArguments) { typeParameter, typeArgument ->
                        typeArgument.typeReference?.typeElement?.let {
                            inferenceContext.typeElementToTypeVariable[it]
                        }?.let { typeVariable ->
                            typeParameter!! to typeVariable
                        }
                    }.mapNotNull { it }.toMap()

                fun BoundType.substituteTypeParameters(): BoundType =
                    BoundTypeImpl(
                        when (label) {
                            is TypeVariableLabel -> (label as TypeVariableLabel)
                                .typeVariable
                                .safeAs<TypeElementBasedTypeVariable>()
                                ?.typeElement
                                ?.safeAs<TypeParameterElementData>()
                                ?.let { typeParameterData ->
                                    typeParameterBindings[typeParameterData.typeParameterDescriptor]
                                }?.let { typeVariable ->
                                    TypeVariableLabel(typeVariable)
                                } ?: label
                            else -> label
                        },
                        typeParameters.map { typeParameter ->
                            TypeParameter(
                                typeParameter.boundType.substituteTypeParameters(),
                                typeParameter.variance
                            )
                        }
                    ).withEnhancementFrom(this)

                val argumentToParameters = call.candidateDescriptor.valueParameters.let { parameters ->
                    valueArguments.mapIndexed { i, arguments ->
                        val parameter = parameters[i]
                        val parameterBoundType =
                            inferenceContext.declarationDescriptorToTypeVariable[parameter]
                                ?.asBoundType()
                                ?.substituteTypeParameters()
                                ?: parameter.original.type.boundType(
                                    null,
                                    expression.getQualifiedExpressionForSelector()
                                        ?.receiverExpression
                                        ?.boundType(inferenceContext),
                                    call,
                                    false,
                                    inferenceContext
                                )
                        parameter.type.isArrayOfNothing()

                        val parameterBoundTypeConsideringVararg =
                            if (parameter.isVararg && KotlinBuiltIns.isArrayOrPrimitiveArray(parameter.type)) {
                                if (KotlinBuiltIns.isPrimitiveArray(parameter.type))
                                    BoundTypeImpl(
                                        GenericLabel(NoClassReference),//not important as it just a primitive type
                                        emptyList()
                                    ) else parameterBoundType.typeParameters[0].boundType
                            } else parameterBoundType
                        arguments.arguments.map { argument ->
                            parameterBoundTypeConsideringVararg to argument
                        }
                    }
                }.flatten()
                for ((parameter, argument) in argumentToParameters) {
                    val argumentExpression = argument.getArgumentExpression() ?: continue
                    argumentExpression.isSubtypeOf(parameter, ConstraintPriority.PARAMETER)
                }
            }

            expression is KtForExpression -> {
                val loopParameterTypeVariable =
                    expression.loopParameter?.typeReference?.typeElement?.let { typeElement ->
                        inferenceContext.typeElementToTypeVariable[typeElement]
                    }
                if (loopParameterTypeVariable != null) {
                    val loopRangeBoundType = expression.loopRange?.boundType(inferenceContext) ?: return
                    val boundType =
                        expression.loopRangeElementType()
                            ?.boundType(null, loopRangeBoundType, null, false, inferenceContext)
                            ?: return
                    loopParameterTypeVariable.isSubtypeOf(
                        boundType.typeParameters.firstOrNull()?.boundType ?: return,
                        ConstraintPriority.ASSIGNMENT
                    )
                }
            }
        }
        Unit
    }

    private fun KtForExpression.loopRangeElementType(): KotlinType? {
        val loopRangeType = loopRange?.getType(analyze(resolutionFacade)) ?: return null
        return loopRangeType
            .constructor
            .declarationDescriptor
            ?.safeAs<ClassDescriptor>()
            ?.getMemberScope(loopRangeType.arguments)
            ?.getDescriptorsFiltered(DescriptorKindFilter.FUNCTIONS) {
                it.asString() == "iterator"
            }?.filterIsInstance<FunctionDescriptor>()
            ?.firstOrNull { it.valueParameters.isEmpty() }
            ?.original
            ?.returnType
    }
}

