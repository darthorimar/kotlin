/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.nj2k.inference.common.collectors

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.nj2k.inference.common.*
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class FunctionConstraintsCollector(
    private val superFunctionsProvider: SuperFunctionsProvider
) : ConstraintsCollector() {
    override fun ConstraintBuilder.collectConstraints(
        element: KtElement,
        boundTypeCalculator: BoundTypeCalculator,
        resolutionFacade: ResolutionFacade
    ) = with(boundTypeCalculator) {
        if (element !is KtFunction) return
        val ktClass = element.containingClassOrObject ?: return
        val classDescriptor = ktClass.resolveToDescriptorIfAny(resolutionFacade) ?: return
        val returnTypeVariable = element.typeReference?.typeElement?.let {
            inferenceContext.typeElementToTypeVariable[it]
        } ?: return

        val superFunctions = superFunctionsProvider.provideSuperFunctionDescriptors(element) ?: return
        val substitutor = inferenceContext.classSupstitutions[classDescriptor] ?: return


        for (superFunction in superFunctions) {
            val superClass = superFunction.containingDeclaration as? ClassDescriptor ?: continue
            val superReturnType = superFunction.original.returnType ?: continue

            val usedTypeVariables = hashSetOf<TypeVariable>()
            for ((typeElement, typeParameter) in calculateTypeSubstitution(
                element.typeReference?.typeElement!!,
                superReturnType
            )) {
                val superEntryTypeElement = substitutor[superClass, typeParameter] ?: continue
                typeElement.isTheSameTypeAs(superEntryTypeElement, ConstraintPriority.SUPER_DECLARATION)

                inferenceContext.typeElementToTypeVariable[typeElement]?.also { usedTypeVariables += it }
            }

            val superFunctionPsi = superFunction.original.findPsi() as? KtNamedFunction
            if (superFunctionPsi != null) {
                val superReturnTypeVariable = superFunctionPsi.typeReference?.typeElement?.let {
                    inferenceContext.typeElementToTypeVariable[it]
                }
                superReturnTypeVariable?.isTheSameTypeAs(
                    returnTypeVariable,
                    ConstraintPriority.SUPER_DECLARATION,
                    usedTypeVariables
                )
            }
        }
    }

    private fun calculateTypeSubstitution(
        typeElement: KtTypeElement,
        superType: KotlinType
    ): List<Pair<KtTypeElement, TypeParameterDescriptor>> {
        val subsitution = superType.constructor.declarationDescriptor
            ?.safeAs<TypeParameterDescriptor>()?.let { typeElement to it }
        return typeElement.typeArgumentsAsTypes.zip(superType.arguments).flatMap { (argumentTypeElement, argumentType) ->
            calculateTypeSubstitution(argumentTypeElement?.typeElement!!, argumentType.type)
        } + listOfNotNull(subsitution)
    }

}