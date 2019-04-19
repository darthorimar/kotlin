/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.nj2k

import com.google.gson.Gson
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.*
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.j2k.IdeaJavaToKotlinServices
import org.jetbrains.kotlin.idea.j2k.J2kPostProcessor
import org.jetbrains.kotlin.idea.refactoring.getLineCount
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.OldJavaToKotlinConverter
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class ProjectConversionTest : KotlinLightCodeInsightFixtureTestCase() {

    val path = "home/ilya/code/guava/"
    val srcPath = "${path}/guava/src/"

    val libraries = listOf(
        "/home/ilya/.m2/repository/com/google/j2objc/j2objc-annotations/1.1/j2objc-annotations-1.1.jar",
        "/home/ilya/.m2/repository/com/google/errorprone/error_prone_annotations/2.3.2/error_prone_annotations-2.3.2.jar",
        "/home/ilya/.m2/repository/org/checkerframework/checker-qual/2.5.2/checker-qual-2.5.2.jar",
        "/home/ilya/.m2/repository/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar",
        "/home/ilya/.m2/repository/com/google/guava/failureaccess/1.0.1/failureaccess-1.0.1.jar",
        "/home/ilya/.m2/repository/org/codehaus/mojo/animal-sniffer-annotations/1.17/animal-sniffer-annotations-1.17.jar"
    )


    data class AllJ2kResults(
        val file: PsiJavaFile,
        val newJ2kResult: J2kResult,
        val oldJ2kResult: J2kResult
    )

    fun AllJ2kResults.toSerializable() =
        AllJ2kSerializableResults(
            file.virtualFile.canonicalPath!!,
            file.getLineCount(),
            newJ2kResult.toSerializable(),
            oldJ2kResult.toSerializable()
        )

    interface J2kResult
    data class J2kSuccess(val errors: List<Diagnostic>) : J2kResult
    data class J2kFailure(val exception: Throwable) : J2kResult

    fun J2kResult.toSerializable() =
        when (this) {
            is J2kSuccess ->
                J2kSerializableSuccess(
                    errors.map { DefaultErrorMessages.render(it).replace('\n', ' ') },
                    errors.size
                )
            is J2kFailure ->
                J2kSerializableFailure(getStackTrace(exception))
            else -> error("")
        }

    interface J2kSerializableResult
    data class J2kSerializableSuccess(val errors: List<String>, val errorsCount: Int) : J2kSerializableResult
    data class J2kSerializableFailure(val exception: String) : J2kSerializableResult
    data class AllJ2kSerializableResults(
        val filePath: String,
        val loc: Int,
        val newJ2kResult: J2kSerializableResult,
        val oldJ2kResult: J2kSerializableResult
    )

    fun getStackTrace(e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }


    override fun setUp() {
        super.setUp()
        LanguageLevelProjectExtension.getInstance(project).languageLevel = LanguageLevel.JDK_1_8

        JavaCodeStyleSettings.getInstance(project).USE_EXTERNAL_ANNOTATIONS = true
    }

    override fun tearDown() {
        JavaCodeStyleSettings.getInstance(project).USE_EXTERNAL_ANNOTATIONS = false
        super.tearDown()
    }

    fun test() {
        val srcRoot = myFixture.copyDirectoryToProject(srcPath, "")
        srcRoot.j2k()
    }

    override fun getTestDataPath(): String = "/"

    fun VirtualFile.j2k() {
        val javaFiles = allJavaFiles()?.sortedBy { it.getLineCount() } ?: run {
            println("No java files found")
            return
        }
        val gson = Gson()
        val data = mutableListOf<AllJ2kSerializableResults>()
        fun J2kSerializableResult.asString() =
            when (this) {
                is J2kSerializableSuccess -> errorsCount.toString()
                is J2kSerializableFailure -> "null"
                else -> error("")
            }
        for ((i, file) in javaFiles.withIndex()) {
            println("$i/${javaFiles.size}: ${file.virtualFile.canonicalPath}:")
            val newResult = file.toKotlinFile(true)
            val oldResult = file.toKotlinFile(false)
//            println("   New: $newResult")
//            println("   Old: $oldResult")
//            println()
            val result = AllJ2kResults(file, newResult, oldResult).toSerializable()
//            data.add(result.toSerializable())
//            println(gson.toJson(result.toSerializable()))
            File("/home/ilya/j2k_result.csv").appendText(
                result.let {
                    "${it.filePath}, ${it.loc}, ${it.newJ2kResult.asString()}, ${it.oldJ2kResult.asString()}\n"
                }
            )
            File("/home/ilya/j2k_result.json").appendText(
                gson.toJson(result) + ",\n"
            )
        }
    }

    private fun PsiJavaFile.toKotlinFile(useNewJ2k: Boolean): J2kResult =
        try {
            val kotlinText = if (useNewJ2k) toKotlinTextNewJ2k() else toKotlinTextOldJ2k()
            J2kSuccess(KtPsiFactory(this).createAnalyzableFile("nya.kt", kotlinText, this).errors())
        } catch (e: Throwable) {
            J2kFailure(e)
        }

    private fun KtFile.errors(): List<Diagnostic> {
        val diagnostics = analyzeWithContent().diagnostics
        return diagnostics.filter { it.severity == Severity.ERROR }
    }

    private fun PsiJavaFile.toKotlinTextNewJ2k(): String {
        val converter = NewJavaToKotlinConverter(project, ConverterSettings.defaultSettings, IdeaJavaToKotlinServices)
        return converter.filesToKotlin(listOf(this), NewJ2kPostProcessor(formatCode = true))
            .results.first()
    }

    private fun PsiJavaFile.toKotlinTextOldJ2k(): String {
        val converter = OldJavaToKotlinConverter(project, ConverterSettings.defaultSettings, IdeaJavaToKotlinServices)
        return converter.filesToKotlin(listOf(this), J2kPostProcessor(formatCode = true))
            .results.first()
    }

    private fun VirtualFile.allJavaFiles(): List<PsiJavaFile>? {
        val psiRoot = PsiManager.getInstance(project).findDirectory(this) ?: return null
        val javaFiles = mutableListOf<PsiJavaFile>()
        psiRoot.accept(object : PsiElementVisitor() {
            override fun visitDirectory(dir: PsiDirectory) {
                dir.acceptChildren(this)
            }

            override fun visitFile(file: PsiFile) {
                if (file is PsiJavaFile) {
                    javaFiles += file
                }
            }
        })
        return javaFiles
    }


    override fun getProjectDescriptor(): KotlinWithJdkAndRuntimeLightProjectDescriptor =
        object : KotlinWithJdkAndRuntimeLightProjectDescriptor() {
            override fun getSdk(): Sdk? {
                val sdk = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE_FULL_JDK.sdk!!
                runWriteAction {
                    val modificator: SdkModificator = sdk.sdkModificator
                    JavaSdkImpl.attachJdkAnnotations(modificator)
                    modificator.commitChanges()
                }
                return sdk
            }

            override fun configureModule(module: Module, model: ModifiableRootModel) {
                super.configureModule(module, model)
                model.getModuleExtension(JavaModuleExternalPaths::class.java)
                for (lib in libraries) {
                    val jarFile = File(lib)
                    assert(jarFile.exists())
                    val name = jarFile.name
                    model.addLibraryEntry(createLibrary(jarFile, name))
                }

            }

            private fun createLibrary(jarFile: File, name: String): Library {
                val library = LibraryTablesRegistrar.getInstance()!!.getLibraryTable(project).createLibrary(name)
                val model = library.modifiableModel
                model.addRoot(VfsUtil.getUrlForLibraryRoot(jarFile), OrderRootType.CLASSES)
                model.commit()
                return library
            }
        }

}