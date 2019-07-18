/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.nj2k.conversions

import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.callOn
import org.jetbrains.kotlin.nj2k.isCallOf
import org.jetbrains.kotlin.nj2k.symbols.deepestFqName
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.impl.JKKtCallExpressionImpl
import org.jetbrains.kotlin.nj2k.tree.impl.JKKtQualifierImpl
import org.jetbrains.kotlin.nj2k.tree.impl.JKNoTypeImpl
import org.jetbrains.kotlin.nj2k.tree.impl.JKQualifiedExpressionImpl
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract


class JavaStreamApiConversion(private val context: NewJ2kConverterContext) : RecursiveApplicableConversionBase() {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKQualifiedExpression) return recurse(element)
        if (element.selector.asCollectStreamCall() == null) recurse(element)
        val unwrapped = element.unwrap()
        val callSequence = unwrapped.asCallSequence() ?: return recurse(element)
        return recurse(callSequence.applyConversion(element))
    }

    private fun List<JKExpression>.asCallSequence(): StreamCallSequence? {
        if (size < 2) return null
        val createStream = first().asCreateStreamCall() ?: return null
        val collectStream = last().asCollectStreamCall() ?: return null

        val operations = subList(1, lastIndex).map { operation ->
            operation.asOperationCall() ?: return null
        }

        return StreamCallSequence(createStream, operations, collectStream)
    }

    private fun JKExpression.asOperationCall(): Operation? {
        if (this !is JKMethodCallExpression) return null
        val type = operationsConversionByFqName[identifier.deepestFqName()]
            ?.firstOrNull { it.javaArgumentsCount == arguments.arguments.size }
            ?: return null
        return Operation(type, arguments, typeArgumentList, this)
    }

    private fun JKExpression.asCreateStreamCall(): CreateStream? {
        if (this !is JKQualifiedExpression) return null
        val call = selector as? JKMethodCallExpression ?: return null
        val type = createStreamByFqName[call.identifier.deepestFqName()]
            ?.firstOrNull { it.takeIf(call) }
            ?: return null
        return CreateStream(type, receiver, call, this)
    }

    private fun JKExpression.asCollectStreamCall(): CollectStream? {
        if (this !is JKMethodCallExpression) return null
        val type = collectStreamFqName[identifier.deepestFqName()]
            ?.firstOrNull { it.takeIf(this) }
            ?: return null
        return CollectStream(type, this, this)
    }

    private fun StreamCallSequence.applyConversion(originalQualifiedExpression: JKQualifiedExpression): JKExpression {
        val createStreamKt = createStream.type
            .toKotlin(
                createStream.receiver.detached(),
                createStream.call.detached()
            )//.withNonCodeElementsFromWithParent(createStream.prototype)
        val operationsKt = operations.map { operation ->
            JKKtCallExpressionImpl(
                provideMethodSymbol(operation.type.kotlinFqName),
                operation.arguments.detached().cleanFunctionalTypeLambdaArguments(),
                typeArgumentList = operation.typeArguments.detached()
            )
        }
        val collectorKt = collectStream.type.toKotlin(collectStream.call.detached())
        return (listOf(createStreamKt) + operationsKt + collectorKt)
            .toQualifiedSequence()
            .also { it.takeStructureFrom(originalQualifiedExpression) }
    }

    private fun JKExpression.takeStructureFrom(other: JKExpression) {
        takeNonCodeElementsFrom(other)
        if (this is JKQualifiedExpression && other is JKQualifiedExpression) {
            receiver.takeStructureFrom(other.receiver)
            selector.takeNonCodeElementsFrom(other.selector)
        }
    }

    private fun JKArgumentList.cleanFunctionalTypeLambdaArguments() = also {
        arguments.forEach { argument ->
            argument.value.safeAs<JKLambdaExpression>()?.functionalType?.type = JKNoTypeImpl
        }
    }

    private fun <T : JKTreeElement> T.detached() = detached(parent!!)

    private fun provideMethodSymbol(fqName: String) =
        context.symbolProvider.provideMethodSymbol(fqName)

    private fun JKQualifiedExpression.unwrap(): List<JKExpression> =
        generateSequence(this) { qualified ->
            qualified.receiver
                .safeAs<JKQualifiedExpression>()
                ?.takeIf { it.receiver is JKQualifiedExpression }
        }.toList().foldRight(emptyList()) { qualified, acc ->
            if (acc.isEmpty()) listOf(qualified.receiver, qualified.selector)
            else acc + qualified.selector
        }

    private fun List<JKExpression>.toQualifiedSequence(): JKExpression =
        reduce { receiver, selector ->
            JKQualifiedExpressionImpl(receiver, JKKtQualifierImpl.DOT, selector)
        }

    private data class StreamCallSequence(
        val createStream: CreateStream,
        val operations: List<Operation>,
        val collectStream: CollectStream
    )

    private data class Operation(
        val type: OperationType,
        val arguments: JKArgumentList,
        val typeArguments: JKTypeArgumentList,
        val prototype: JKExpression
    )

    private data class CreateStream(
        val type: CreateStreamType,
        val receiver: JKExpression,
        val call: JKMethodCallExpression,
        val prototype: JKExpression
    )

    private data class CollectStream(
        val type: CollectStreamType,
        val call: JKMethodCallExpression,
        val prototype: JKExpression
    )

    private data class OperationType(
        val javaFqName: String,
        val kotlinFqName: String,
        val javaArgumentsCount: Int
    )

    private data class CreateStreamType(
        val javaFqName: String,
        val takeIf: (JKMethodCallExpression) -> Boolean,
        val toKotlin: (JKExpression, JKMethodCallExpression) -> JKExpression
    )

    private interface CollectStreamType {
        val javaFqName: String
        val takeIf: (JKMethodCallExpression) -> Boolean
        val toKotlin: (JKMethodCallExpression) -> JKExpression
    }

    private data class CollectStreamTypeByCallingCollect(
        val collectorFqName: String,
        val collectorToKotlin: (JKExpression) -> JKExpression
    ) : CollectStreamType {
        override val javaFqName = "java.util.stream.Stream.collect"

        override val takeIf = { call: JKMethodCallExpression ->
            call.arguments.arguments.singleOrNull()?.value?.let { expression ->
                val selector = if (expression is JKQualifiedExpression) expression.selector else expression
                selector.isCallOf(collectorFqName)
            } == true
        }

        override val toKotlin = { call: JKMethodCallExpression ->
            collectorToKotlin(call.arguments.arguments.single().value)
        }
    }

    private data class CollectStreamTypeImpl(
        override val javaFqName: String,
        override val takeIf: (JKMethodCallExpression) -> Boolean,
        override val toKotlin: (JKMethodCallExpression) -> JKExpression
    ) : CollectStreamType


    private val operationsConversionByFqName = listOf(
        OperationType(
            javaFqName = "java.util.stream.Stream.filter",
            kotlinFqName = "kotlin.collections.filter",
            javaArgumentsCount = 1
        ),
        OperationType(
            javaFqName = "java.util.stream.Stream.map",
            kotlinFqName = "kotlin.collections.map",
            javaArgumentsCount = 1
        ),
        OperationType(
            javaFqName = "java.util.stream.Stream.distinct",
            kotlinFqName = "kotlin.collections.distinct",
            javaArgumentsCount = 0
        ),
        OperationType(
            javaFqName = "java.util.stream.Stream.sorted",
            kotlinFqName = "kotlin.collections.sorted",
            javaArgumentsCount = 0
        ),
        OperationType(
            javaFqName = "java.util.stream.Stream.sorted",
            kotlinFqName = "kotlin.collections.sortedWith",
            javaArgumentsCount = 1
        ),
        OperationType(
            javaFqName = "java.util.stream.Stream.peek",
            kotlinFqName = "kotlin.collections.onEach",
            javaArgumentsCount = 1
        ),
        OperationType(
            javaFqName = "java.util.stream.Stream.limit",
            kotlinFqName = "kotlin.collections.take",
            javaArgumentsCount = 1
        ),
        OperationType(
            javaFqName = "java.util.stream.Stream.skip",
            kotlinFqName = "kotlin.collections.drop",
            javaArgumentsCount = 1
        )
    ).groupBy { it.javaFqName }


    private val createStreamByFqName = listOf(
        CreateStreamType(
            javaFqName = "java.util.Collection.stream",
            takeIf = { argument -> argument.arguments.arguments.isEmpty() },
            toKotlin = { receiver, call ->
                receiver.callOn(
                    provideMethodSymbol("kotlin.collections.asSequence"),
                    typeArguments = call::typeArgumentList.detached()
                )
            }
        ),
        CreateStreamType(
            javaFqName = "java.util.stream.Stream.of",
            takeIf = { argument ->
                argument.arguments.arguments.firstOrNull()?.value?.type(context.symbolProvider)?.isArrayType() != true
            },
            toKotlin = { _, call ->
                JKKtCallExpressionImpl(
                    provideMethodSymbol("kotlin.collections.sequenceOf"),
                    arguments = call::arguments.detached(),
                    typeArgumentList = call::typeArgumentList.detached()
                )
            }
        ),

        CreateStreamType(
            javaFqName = "java.util.stream.Stream.of",
            takeIf = { argument ->
                argument.arguments.arguments.singleOrNull()?.value?.safeAs<JKPrefixExpression>()?.operator?.token?.text == "*"
            },
            toKotlin = { _, call ->
                val array = call.arguments.arguments.single().value.cast<JKPrefixExpression>()::expression.detached()
                array.callOn(provideMethodSymbol("kotlin.collections.asSequence"))
            }
        ),
        CreateStreamType(
            javaFqName = "java.util.Arrays.stream",
            takeIf = { argument ->
                argument.arguments.arguments.singleOrNull()?.value?.type(context.symbolProvider)?.isArrayType() == true
            },
            toKotlin = { _, call ->
                val array = call.arguments.arguments.single()::value.detached()
                array.callOn(provideMethodSymbol("kotlin.collections.asSequence"))
            }
        ),
        CreateStreamType(
            javaFqName = "java.util.stream.Stream.iterate",
            takeIf = { argument ->
                argument.arguments.arguments.size == 2
            },
            toKotlin = { _, call ->
                JKKtCallExpressionImpl(
                    provideMethodSymbol("kotlin.sequences.generateSequence"),
                    arguments = call::arguments.detached().cleanFunctionalTypeLambdaArguments(),
                    typeArgumentList = call::typeArgumentList.detached()
                )
            }
        ),
        CreateStreamType(
            javaFqName = "java.util.stream.Stream.generate",
            takeIf = { argument ->
                argument.arguments.arguments.size == 1
            },
            toKotlin = { _, call ->
                JKKtCallExpressionImpl(
                    provideMethodSymbol("kotlin.sequences.generateSequence"),
                    arguments = call::arguments.detached().cleanFunctionalTypeLambdaArguments(),
                    typeArgumentList = call::typeArgumentList.detached()
                )
            }
        )

    ).groupBy { it.javaFqName }

    private val collectStreamFqName = listOf(
        CollectStreamTypeByCallingCollect(
            collectorFqName = "java.util.stream.Collectors.toList",
            collectorToKotlin = {
                JKKtCallExpressionImpl(
                    provideMethodSymbol("kotlin.collections.toList")
                )
            }
        ),
        CollectStreamTypeByCallingCollect(
            collectorFqName = "java.util.stream.Collectors.toSet",
            collectorToKotlin = {
                JKKtCallExpressionImpl(
                    provideMethodSymbol("kotlin.collections.toSet")
                )
            }
        ),
        CollectStreamTypeImpl(
            javaFqName = "java.util.stream.Stream.count",
            takeIf = { it.arguments.arguments.isEmpty() },
            toKotlin = {
                JKKtCallExpressionImpl(
                    provideMethodSymbol("kotlin.collections.count")
                )
            }

        ),
        CollectStreamTypeImpl(
            javaFqName = "java.util.stream.Stream.anyMatch",
            takeIf = { it.arguments.arguments.size == 1 },
            toKotlin = { call ->
                JKKtCallExpressionImpl(
                    provideMethodSymbol("kotlin.collections.any"),
                    arguments = call.arguments.detached().cleanFunctionalTypeLambdaArguments()
                )
            }
        ),
        CollectStreamTypeImpl(
            javaFqName = "java.util.stream.Stream.allMatch",
            takeIf = { it.arguments.arguments.size == 1 },
            toKotlin = { call ->
                JKKtCallExpressionImpl(
                    provideMethodSymbol("kotlin.collections.all"),
                    arguments = call.arguments.detached().cleanFunctionalTypeLambdaArguments()
                )
            }
        ),
        CollectStreamTypeImpl(
            javaFqName = "java.util.stream.Stream.noneMatch",
            takeIf = { it.arguments.arguments.size == 1 },
            toKotlin = { call ->
                JKKtCallExpressionImpl(
                    provideMethodSymbol("kotlin.collections.none"),
                    arguments = call.arguments.detached().cleanFunctionalTypeLambdaArguments()
                )
            }
        ),
        CollectStreamTypeImpl(
            javaFqName = "java.util.stream.Stream.forEach",
            takeIf = { it.arguments.arguments.size == 1 },
            toKotlin = { call ->
                JKKtCallExpressionImpl(
                    provideMethodSymbol("kotlin.collections.forEach"),
                    arguments = call.arguments.detached().cleanFunctionalTypeLambdaArguments()
                )
            }
        )
    ).groupBy { it.javaFqName }


}