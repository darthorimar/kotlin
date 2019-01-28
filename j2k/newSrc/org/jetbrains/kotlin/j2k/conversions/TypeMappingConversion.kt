/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.j2k.conversions

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.j2k.*
import org.jetbrains.kotlin.j2k.ast.Mutability
import org.jetbrains.kotlin.j2k.ast.Nullability
import org.jetbrains.kotlin.j2k.tree.*
import org.jetbrains.kotlin.j2k.tree.impl.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

class TypeMappingConversion(val context: ConversionContext) : RecursiveApplicableConversionBase() {
    private val typeFlavorCalculator = TypeFlavorCalculator(object : TypeFlavorConverterFacade {
        override val referenceSearcher: ReferenceSearcher
            get() = context.converter.converterServices.oldServices.referenceSearcher
        override val javaDataFlowAnalyzerFacade: JavaDataFlowAnalyzerFacade
            get() = context.converter.converterServices.oldServices.javaDataFlowAnalyzerFacade
        override val resolverForConverter: ResolverForConverter
            get() = context.converter.converterServices.oldServices.resolverForConverter

        override fun inConversionScope(element: PsiElement): Boolean = context.inConversionContext(element)
    })

    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        return when (element) {
            is JKTypeElement -> {
                val newType = element.type
                    .fixRawType(element)
                    .mapType(element)
                    .refineNullability(element)
                JKTypeElementImpl(newType)
            }
            is JKJavaNewExpression -> {
                val newClassSymbol = element.classSymbol.mapClassSymbol(null)
                recurse(
                    JKJavaNewExpressionImpl(
                        newClassSymbol,
                        element::arguments.detached(),
                        element::typeArgumentList.detached().fixTypeArguments(newClassSymbol),
                        element::classBody.detached()
                    )
                )
            }
            else -> recurse(element)
        }
    }

    private fun JKTypeArgumentList.fixTypeArguments(classSymbol: JKClassSymbol): JKTypeArgumentList {
        if (typeArguments.isNotEmpty()) {
            return JKTypeArgumentListImpl(
                typeArguments.map { typeArgument ->
                    JKTypeElementImpl(typeArgument.type.mapType(null))
                }
            )
        }
        val typeParametersCount = classSymbol.expectedTypeParametersCount()
        return when (typeParametersCount) {
            0 -> this
            else -> JKTypeArgumentListImpl(List(typeParametersCount) {
                JKTypeElementImpl(
                    kotlinTypeByName(
                        KotlinBuiltIns.FQ_NAMES.any.toSafe().asString(),
                        context.symbolProvider,
                        Nullability.Nullable
                    )
                )
            })
        }
    }

    private fun JKType.refineNullability(typeElement: JKTypeElement): JKType {
        if (nullability == Nullability.Default && this is JKClassType) {
            val newNullability = calculateNullability(typeElement)
            if (newNullability != nullability) {
                return JKClassTypeImpl(classReference, parameters, newNullability)
            }
        }
        return this
    }

    private fun JKType.fixRawType(typeElement: JKTypeElement) =
        when (typeElement.parent) {
            is JKKtIsExpression ->
                addTypeParametersToRawProjectionType(JKStarProjectionTypeImpl())
                    .updateNullability(Nullability.NotNull)
            is JKTypeCastExpression ->
                addTypeParametersToRawProjectionType(JKStarProjectionTypeImpl())

            else ->
                addTypeParametersToRawProjectionType(
                    JKStarProjectionTypeImpl()
                )
        }

    private fun JKType.mapType(typeElement: JKTypeElement?): JKType =
        when (this) {
            is JKJavaPrimitiveType -> mapPrimitiveType()
            is JKClassType -> mapClassType(typeElement)
            is JKJavaVoidType ->
                kotlinTypeByName(
                    KotlinBuiltIns.FQ_NAMES.unit.toSafe().asString(),
                    context.symbolProvider,
                    Nullability.NotNull
                )
            is JKJavaArrayType ->
                JKClassTypeImpl(
                    context.symbolProvider.provideByFqName(type.arrayFqName()),
                    if (type is JKJavaPrimitiveType) emptyList() else listOf(type.mapType(typeElement)),
                    type.nullability
                )
            else -> this
        }

    private fun JKClassSymbol.mapClassSymbol(typeElement: JKTypeElement?): JKClassSymbol {
        if (this is JKUniverseClassSymbol) return this
        val newFqName = typeElement?.let { kotlinCollectionClassName(it) }
            ?: kotlinStandardType()
            ?: fqName
            ?: return this
        return context.symbolProvider.provideByFqName(newFqName)
    }

    private fun JKClassType.mapClassType(typeElement: JKTypeElement?): JKClassType =
        JKClassTypeImpl(
            classReference.mapClassSymbol(typeElement),
            parameters.map { it.mapType(null) },
            nullability
        )


    private fun JKClassSymbol.kotlinCollectionClassName(typeElement: JKTypeElement?): String? {
        val isStructureMutable = calculateStructureMutability(typeElement)
        return if (isStructureMutable) toKotlinMutableTypesMap[fqName]
        else toKotlinTypesMap[fqName]
    }

    private fun JKClassSymbol.kotlinStandardType(): String? =
        fqName?.let {
            JavaToKotlinClassMap.mapJavaToKotlin(FqName(it))?.asString()
        }

    private fun JKJavaPrimitiveType.mapPrimitiveType(): JKClassType {
        val fqName = jvmPrimitiveType.primitiveType.typeFqName
        return JKClassTypeImpl(
            context.symbolProvider.provideByFqName(ClassId.topLevel(fqName)),
            nullability = Nullability.NotNull
        )
    }

    private fun calculateNullability(typeElement: JKTypeElement?): Nullability {
        val parent = typeElement?.parent ?: return Nullability.Default
        val psi = parent.psi
        return when (parent) {
            is JKMethod ->
                parent.nullabilityBySuperMethod(context.symbolProvider).defaultToNull()
                    ?: psi?.let { typeFlavorCalculator.methodNullability(it as PsiMethod) }
                        .nullToDefault()

            is JKVariable -> psi?.let {
                typeFlavorCalculator.variableNullability(psi as PsiVariable)
            }.nullToDefault()

            else -> Nullability.Default
        }
    }

    private fun Nullability.defaultToNull() =
        if (this == Nullability.Default) null else this

    private fun Nullability?.nullToDefault() =
        this ?: Nullability.Default

    private fun calculateStructureMutability(typeElement: JKTypeElement?): Boolean {
        val parent = typeElement?.parent ?: return false
        val psi = parent.psi ?: return false
        return when (parent) {
            is JKVariable -> typeFlavorCalculator.variableMutability(psi as PsiVariable) == Mutability.Mutable
            is JKMethod -> typeFlavorCalculator.methodMutability(psi as PsiMethod) == Mutability.Mutable
            else -> false
        }
    }
}