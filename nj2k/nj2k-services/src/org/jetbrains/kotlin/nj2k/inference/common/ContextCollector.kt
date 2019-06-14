/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.nj2k.inference.common

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

abstract class ContextCollector(private val resolutionFacade: ResolutionFacade) {
    private fun KotlinType.classReference(): ClassReference? =
        when (val descriptor = constructor.declarationDescriptor) {
            is ClassDescriptor -> descriptor.classReference
            is TypeParameterDescriptor -> TypeParameterReference(descriptor)
            else -> null
        }

    private fun KtTypeReference.classReference(): ClassReference? =
        analyze()[BindingContext.TYPE, this]?.classReference()

    private fun KtTypeElement.toData(): TypeElementData {
        val typeReference = parent as? KtTypeReference ?: return TypeElementDataImpl(this)
        val typeParameterDescriptor = analyze(resolutionFacade)[BindingContext.TYPE, typeReference]
            ?.constructor
            ?.declarationDescriptor
            ?.safeAs<TypeParameterDescriptor>() ?: return TypeElementDataImpl(this)
        return TypeParameterElementData(this, typeParameterDescriptor)
    }


    fun collectTypeVariables(elements: List<KtElement>): InferenceContext {
        val declarationToTypeVariable = mutableMapOf<KtNamedDeclaration, TypeVariable>()
        val typeElementToTypeVariable = mutableMapOf<KtTypeElement, TypeVariable>()
        fun KtTypeReference.toBoundType(): BoundType? {
            val typeElement = typeElement ?: return null
            val classReference = classReference() ?: NoClassReference
            if (classReference.descriptor?.defaultType?.isUnit() == true) return null

            val state = classReference.getState(typeElement)
            val typeArguments =
                if (classReference is DescriptorClassReference) {
                    typeElement.typeArgumentsAsTypes.zip(classReference.descriptor.declaredTypeParameters) { typeArgument, typeParameter ->
                        TypeParameter(
                            if (typeArgument == null) {
                                BoundTypeImpl(StarProjectionlLabel, emptyList())
                            } else typeArgument.toBoundType()!!,
                            typeParameter.variance
                        )
                    }
                } else emptyList()

            return if (state == null) {
                BoundTypeImpl(
                    GenericLabel(classReference),
                    typeArguments
                )
            } else {
                val typeVariable = TypeElementBasedTypeVariable(
                    classReference,
                    typeArguments,
                    typeElement.toData(),
                    state
                )
                typeElementToTypeVariable[typeElement] = typeVariable
                typeVariable.asBoundType()
            }
        }

        fun KotlinType.toBoundType(): BoundType? {
            val classReference = classReference() ?: NoClassReference
            val state = classReference.getState(typeElement = null)

            val typeArguments =
                if (classReference is DescriptorClassReference) {
                    arguments.zip(classReference.descriptor.declaredTypeParameters) { typeArgument, typeParameter ->
                        TypeParameter(
                            typeArgument.type.toBoundType()!!,
                            typeParameter.variance
                        )
                    }
                } else emptyList()

            return if (state == null) {
                BoundTypeImpl(
                    GenericLabel(classReference),
                    typeArguments
                )
            } else {
                val typeVariable = TypeBasedTypeVariable(
                    classReference,
                    typeArguments,
                    this,
                    state
                )
                typeVariable.asBoundType()
            }
        }

        for (element in elements) {
            element.forEachDescendantOfType<KtExpression> { expression ->
                if (expression is KtCallableDeclaration
                    && (expression is KtParameter
                            || expression is KtProperty
                            || expression is KtNamedFunction)
                ) {
                    val typeReference = expression.typeReference ?: return@forEachDescendantOfType
                    val typeVariable = typeReference.toBoundType()?.typeVariable ?: return@forEachDescendantOfType
                    declarationToTypeVariable[expression] = typeVariable
                }

                when (expression) {
                    is KtCallExpression ->
                        for (typeArgument in expression.typeArguments) {
                            typeArgument.typeReference?.toBoundType()
                        }
                    is KtLambdaExpression -> {
                        val context = expression.analyze(resolutionFacade)
                        val returnType = expression.getType(context)?.arguments?.lastOrNull()?.type ?: return@forEachDescendantOfType
                        val typeVariable = returnType.toBoundType()?.typeVariable ?: return@forEachDescendantOfType
                        declarationToTypeVariable[expression.functionLiteral] = typeVariable
                    }
                    is KtBinaryExpressionWithTypeRHS -> {
                        val typeReference = expression.right ?: return@forEachDescendantOfType
                        val typeElement = typeReference.typeElement ?: return@forEachDescendantOfType
                        val typeVariable = typeReference.toBoundType()?.typeVariable ?: return@forEachDescendantOfType
                        typeElementToTypeVariable[typeElement] = typeVariable
                    }
                }
            }
        }

        val typeVariables =
            (typeElementToTypeVariable.values + declarationToTypeVariable.values).distinct()
        return InferenceContext(
            elements,
            typeVariables,
            typeElementToTypeVariable,
            declarationToTypeVariable,
            declarationToTypeVariable.mapKeys { (key, _) -> key.resolveToDescriptorIfAny(resolutionFacade)!! }
        )
    }

    abstract fun ClassReference.getState(typeElement: KtTypeElement?): State?
}