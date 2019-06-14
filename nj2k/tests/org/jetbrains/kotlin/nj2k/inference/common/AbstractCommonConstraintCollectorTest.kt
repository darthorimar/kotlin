/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.nj2k.inference.common

import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.nj2k.inference.AbstractConstraintCollectorTest
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.types.KotlinType

abstract class AbstractCommonConstraintCollectorTest : AbstractConstraintCollectorTest() {
    override fun createInferenceFacade(resolutionFacade: ResolutionFacade): InferenceFacade =
        InferenceFacade(
            TestContextCollector(resolutionFacade),
            TestConstraintCollector(resolutionFacade),
            BoundTypeCalculator(resolutionFacade, TestBoundTypeEnhancer()),
            object : StateUpdater() {
                override fun updateStates(inferenceContext: InferenceContext) {}
            },
            isDebugMode = true
        )

    private class TestConstraintCollector(resolutionFacade: ResolutionFacade) : ConstraintsCollector(resolutionFacade) {
        override fun ConstraintBuilder.collectAdditionalConstraints(
            expression: KtElement,
            boundTypeCalculator: BoundTypeCalculator,
            inferenceContext: InferenceContext
        ) {
        }
    }

    private class TestContextCollector(resolutionFacade: ResolutionFacade) : ContextCollector(resolutionFacade) {
        override fun ClassReference.getState(typeElement: KtTypeElement?): State? = State.UNKNOWN
    }

    private class TestBoundTypeEnhancer : BoundTypeEnhancer() {
        override fun enhance(
            expression: KtExpression,
            boundType: BoundType,
            inferenceContext: InferenceContext
        ): BoundType = boundType

        override fun enhanceKotlinType(
            type: KotlinType,
            boundType: BoundType,
            inferenceContext: InferenceContext
        ): BoundType = boundType
    }
}