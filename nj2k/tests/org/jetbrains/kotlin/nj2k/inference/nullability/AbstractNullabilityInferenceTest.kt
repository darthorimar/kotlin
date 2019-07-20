/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.nj2k.inference.nullability

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.nj2k.inference.common.*
import org.jetbrains.kotlin.nj2k.inference.common.collectors.CommonConstraintsCollector
import org.jetbrains.kotlin.nj2k.inference.common.collectors.FunctionConstraintsCollector
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.KotlinTestUtils
import java.io.File

abstract class AbstractNullabilityInferenceTest : KotlinLightCodeInsightFixtureTestCase() {
    private fun createInferenceFacade(resolutionFacade: ResolutionFacade): InferenceFacade {
        val typeEnhancer = NullabilityBoundTypeEnhancer(resolutionFacade)
        return InferenceFacade(
            TestContextCollector(resolutionFacade),
            ConstraintsCollectorAggregator(
                resolutionFacade,
                listOf(
                    CommonConstraintsCollector(),
                    FunctionConstraintsCollector(ResolveSuperFunctionsProvider(resolutionFacade)),
                    NullabilityConstraintsCollector()
                )
            ),
            BoundTypeCalculator(resolutionFacade, typeEnhancer),
            NullabilityStateUpdater(),
            isDebugMode = false
        )
    }

    fun doTest(path: String) {
        val file = File(path)
        val text = FileUtil.loadFile(file, true)
        val ktFile = myFixture.configureByText("converterTestFile.kt", text) as KtFile
        val resolutionFacade = ktFile.getResolutionFacade()
        CommandProcessor.getInstance().runUndoTransparentAction {
            ktFile.makeAllTypesNotNull()
            createInferenceFacade(resolutionFacade).runOn(listOf(ktFile))
        }
        KotlinTestUtils.assertEqualsToFile(file, ktFile.text)
    }


    private fun KtFile.makeAllTypesNotNull() = runWriteAction {
        for (typeElement in collectDescendantsOfType<KtTypeElement>()) {
            NullabilityStateUpdater.changeState(typeElement, toNullable = false)
        }
    }

    private class TestContextCollector(resolutionFacade: ResolutionFacade) : ContextCollector(resolutionFacade) {
        override fun ClassReference.getState(typeElement: KtTypeElement?): State? = State.UNKNOWN
    }

    override fun setUp() {
        super.setUp()
        JavaCodeStyleSettings.getInstance(project).USE_EXTERNAL_ANNOTATIONS = true
    }

    override fun tearDown() {
        JavaCodeStyleSettings.getInstance(project).USE_EXTERNAL_ANNOTATIONS = false
        super.tearDown()
    }


    private fun projectDescriptorByFileDirective(): LightProjectDescriptor {
        if (isAllFilesPresentInTest()) return KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE
        val fileText = FileUtil.loadFile(File(testDataPath, fileName()), true)
        return if (InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITH_FULL_JDK"))
            KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE_FULL_JDK
        else KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE
    }

    override fun getProjectDescriptor(): KotlinWithJdkAndRuntimeLightProjectDescriptor =
        object : KotlinWithJdkAndRuntimeLightProjectDescriptor() {
            override fun getSdk(): Sdk? {
                val sdk = projectDescriptorByFileDirective().sdk ?: return null
                runWriteAction {
                    val modificator: SdkModificator = sdk.sdkModificator
                    JavaSdkImpl.attachJdkAnnotations(modificator)
                    modificator.commitChanges()
                }
                return sdk
            }
        }
}