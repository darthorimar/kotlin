/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.nj2k.inference.common

import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.nj2k.ExternalSuperFunctionInfo
import org.jetbrains.kotlin.nj2k.FunctionInfo
import org.jetbrains.kotlin.nj2k.InternalSuperFunctionInfo
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

abstract class SuperFunctionsProvider {
    abstract fun provideSuperFunctionDescriptors(function: KtFunction): List<FunctionDescriptor>?
}

class ResolveSuperFunctionsProvider(private val resolutionFacade: ResolutionFacade) : SuperFunctionsProvider() {
    override fun provideSuperFunctionDescriptors(function: KtFunction): List<FunctionDescriptor>? =
        function.resolveToDescriptorIfAny(resolutionFacade)
            ?.safeAs<FunctionDescriptor>()
            ?.original
            ?.overriddenDescriptors
            ?.toList()
}

class ByInfoSuperFunctionsProvider(
    private val resolutionFacade: ResolutionFacade,
    private val converterContext: NewJ2kConverterContext
) : SuperFunctionsProvider() {
    lateinit var inferenceContext: InferenceContext

    private val labelToFunction by lazy {
        inferenceContext.declarationToTypeVariable.mapNotNull { (declaration, _) ->
            if (declaration !is KtNamedFunction) return@mapNotNull null
            val label = declaration.nameIdentifier?.getLabel() ?: return@mapNotNull null
            label to declaration
        }.toMap()
    }

    override fun provideSuperFunctionDescriptors(function: KtFunction): List<FunctionDescriptor>? =
        function.nameIdentifier?.elementInfo(converterContext)?.firstIsInstanceOrNull<FunctionInfo>()
            ?.superFunctions
            ?.mapNotNull { superFunction ->
                when (superFunction) {
                    is ExternalSuperFunctionInfo -> superFunction.descriptor
                    is InternalSuperFunctionInfo -> labelToFunction[superFunction.label]?.resolveToDescriptorIfAny(resolutionFacade)
                }
            }

}