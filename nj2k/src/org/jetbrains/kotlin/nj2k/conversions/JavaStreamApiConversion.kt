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
import org.jetbrains.kotlin.nj2k.tree.impl.*
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.kotlin.utils.addToStdlib.safeAs


class JavaStreamApiConversion(private val context: NewJ2kConverterContext) : RecursiveApplicableConversionBase() {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        return recurse(element)
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

        return StreamCallSequence(createStream.streamType ?: JKNoTypeImpl, createStream, operations, collectStream)
    }

    private fun JKExpression.asOperationCall(): Operation? {
        if (this !is JKMethodCallExpression) return null
        val type = operationsConversionByFqName[identifier.deepestFqName()]
            ?.firstOrNull { it.javaArgumentsCount == arguments.arguments.size }
            ?: return null
        return Operation(type, arguments, typeArgumentList)
    }

    private fun JKExpression.asCreateStreamCall(): CreateStream? {
        if (this !is JKQualifiedExpression) return null
        val call = selector as? JKMethodCallExpression ?: return null
        val type = createStreamByFqName[call.identifier.deepestFqName()]
            ?.firstOrNull { it.takeIf(call) }
            ?: return null
        return CreateStream(type, receiver, call)
    }

    private fun JKExpression.asCollectStreamCall(): CollectStream? {
        if (this !is JKMethodCallExpression) return null
        val type = collectStreamFqName[identifier.deepestFqName()]
            ?.firstOrNull { it.takeIf(this) }
            ?: return null
        return CollectStream(type, this)
    }

    private fun StreamCallSequence.applyConversion(originalQualifiedExpression: JKQualifiedExpression): JKExpression {
        val createStreamKt = createStream.kind
            .toKotlin(
                createStream.receiver.detached(),
                createStream.call.detached()
            )
        val operationsKt = operations.map { operation ->
            JKKtCallExpressionImpl(
                provideMethodSymbol(operation.kind.kotlinFqName),
                operation.arguments.detached().cleanFunctionalTypeLambdaArguments(),
                typeArgumentList = operation.typeArguments.detached()
            )
        }
        val collectorKt = collectStream.kind.toKotlin(collectStream.call.detached(), streamType)
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

    private class StreamCallSequence(
        val streamType: JKType,
        val createStream: CreateStream,
        val operations: List<Operation>,
        val collectStream: CollectStream
    )

    private class Operation(
        val kind: OperationKind,
        val arguments: JKArgumentList,
        val typeArguments: JKTypeArgumentList
    )

    private class CreateStream(
        val kind: CreateStreamKind,
        val receiver: JKExpression,
        val call: JKMethodCallExpression
    ) {
        val streamType
            get() = kind.streamType(receiver, call)
    }

    private class CollectStream(
        val kind: CollectStreamKind,
        val call: JKMethodCallExpression
    )

    private class OperationKind(
        val javaFqName: String,
        val kotlinFqName: String,
        val javaArgumentsCount: Int
    )

    private class CreateStreamKind(
        val javaFqName: String,
        val takeIf: (JKMethodCallExpression) -> Boolean,
        val toKotlin: (JKExpression, JKMethodCallExpression) -> JKExpression,
        val streamType: (JKExpression, JKMethodCallExpression) -> JKType?
    )

    private interface CollectStreamKind {
        val javaFqName: String
        val takeIf: (JKMethodCallExpression) -> Boolean
        val toKotlin: (JKMethodCallExpression, JKType) -> JKExpression
    }

    private class CollectStreamTypeByCallingCollect(
        val collectorFqName: String,
        val collectorToKotlin: (JKExpression, JKType) -> JKExpression
    ) : CollectStreamKind {
        override val javaFqName = "java.util.stream.Stream.collect"

        override val takeIf = { call: JKMethodCallExpression ->
            call.arguments.arguments.singleOrNull()?.value?.let { expression ->
                val selector = if (expression is JKQualifiedExpression) expression.selector else expression
                selector.isCallOf(collectorFqName)
            } == true
        }

        override val toKotlin = { call: JKMethodCallExpression, streamType: JKType ->
            collectorToKotlin(call.arguments.arguments.single().value, streamType)
        }
    }

    private class CollectStreamKindImpl(
        override val javaFqName: String,
        override val takeIf: (JKMethodCallExpression) -> Boolean,
        override val toKotlin: (JKMethodCallExpression, JKType) -> JKExpression
    ) : CollectStreamKind


    private val operationsConversionByFqName = listOf(
        OperationKind(
            javaFqName = "java.util.stream.Stream.filter",
            kotlinFqName = "kotlin.collections.filter",
            javaArgumentsCount = 1
        ),
        OperationKind(
            javaFqName = "java.util.stream.Stream.map",
            kotlinFqName = "kotlin.collections.map",
            javaArgumentsCount = 1
        ),
        OperationKind(
            javaFqName = "java.util.stream.Stream.distinct",
            kotlinFqName = "kotlin.collections.distinct",
            javaArgumentsCount = 0
        ),
        OperationKind(
            javaFqName = "java.util.stream.Stream.sorted",
            kotlinFqName = "kotlin.collections.sorted",
            javaArgumentsCount = 0
        ),
        OperationKind(
            javaFqName = "java.util.stream.Stream.sorted",
            kotlinFqName = "kotlin.collections.sortedWith",
            javaArgumentsCount = 1
        ),
        OperationKind(
            javaFqName = "java.util.stream.Stream.peek",
            kotlinFqName = "kotlin.collections.onEach",
            javaArgumentsCount = 1
        ),
        OperationKind(
            javaFqName = "java.util.stream.Stream.limit",
            kotlinFqName = "kotlin.collections.take",
            javaArgumentsCount = 1
        ),
        OperationKind(
            javaFqName = "java.util.stream.Stream.skip",
            kotlinFqName = "kotlin.collections.drop",
            javaArgumentsCount = 1
        )
    ).groupBy { it.javaFqName }

    private val createStreamByFqName = listOf(
        CreateStreamKind(
            javaFqName = "java.util.Collection.stream",
            takeIf = { argument -> argument.arguments.arguments.isEmpty() },
            toKotlin = { receiver, call ->
                receiver.callOn(
                    provideMethodSymbol("kotlin.collections.asSequence"),
                    typeArguments = call::typeArgumentList.detached()
                )
            },
            streamType = { receiver, _ ->
                receiver.type(context.symbolProvider)?.safeAs<JKParametrizedType>()?.parameters?.singleOrNull()
            }
        ),
        CreateStreamKind(
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
            },
            streamType = { _, call ->
                call.typeArgumentList.typeArguments.singleOrNull()?.type
            }
        ),

        CreateStreamKind(
            javaFqName = "java.util.stream.Stream.of",
            takeIf = { argument ->
                argument.arguments.arguments.singleOrNull()?.value?.safeAs<JKPrefixExpression>()?.operator?.token?.text == "*"
            },
            toKotlin = { _, call ->
                val array = call.arguments.arguments.single().value.cast<JKPrefixExpression>()::expression.detached()
                array.callOn(provideMethodSymbol("kotlin.collections.asSequence"))
            },
            streamType = { _, call ->
                call.typeArgumentList.typeArguments.singleOrNull()?.type
            }
        ),
        CreateStreamKind(
            javaFqName = "java.util.Arrays.stream",
            takeIf = { argument ->
                argument.arguments.arguments.singleOrNull()?.value?.type(context.symbolProvider)?.isArrayType() == true
            },
            toKotlin = { _, call ->
                val array = call.arguments.arguments.single()::value.detached()
                array.callOn(provideMethodSymbol("kotlin.collections.asSequence"))
            },
            streamType = { _, call ->
                call.typeArgumentList.typeArguments.singleOrNull()?.type
            }
        ),
        CreateStreamKind(
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
            },
            streamType = { _, call ->
                call.typeArgumentList.typeArguments.singleOrNull()?.type
            }
        ),
        CreateStreamKind(
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
            },
            streamType = { _, call ->
                call.typeArgumentList.typeArguments.singleOrNull()?.type
            }
        )

    ).groupBy { it.javaFqName }

    private val collectStreamFqName = listOf(
        CollectStreamTypeByCallingCollect(
            collectorFqName = "java.util.stream.Collectors.toList",
            collectorToKotlin = { call, streamType ->
                call.toKotlinCollector("kotlin.collections.toList", streamType)
            }
        ),
        CollectStreamTypeByCallingCollect(
            collectorFqName = "java.util.stream.Collectors.toSet",
            collectorToKotlin = { call, streamType ->
                call.toKotlinCollector("kotlin.collections.toSet", streamType)
            }
        ),
        CollectStreamKindImpl(
            javaFqName = "java.util.stream.Stream.count",
            takeIf = { it.arguments.arguments.isEmpty() },
            toKotlin = { call, streamType ->
                call.toKotlinCollector("kotlin.collections.count", streamType)
            }
        ),
        CollectStreamKindImpl(
            javaFqName = "java.util.stream.Stream.anyMatch",
            takeIf = { it.arguments.arguments.size == 1 },
            toKotlin = { call, streamType ->
                call.toKotlinCollector("kotlin.collections.any", streamType)
            }
        ),
        CollectStreamKindImpl(
            javaFqName = "java.util.stream.Stream.allMatch",
            takeIf = { it.arguments.arguments.size == 1 },
            toKotlin = { call, streamType ->
                call.toKotlinCollector("kotlin.collections.all", streamType)
            }
        ),
        CollectStreamKindImpl(
            javaFqName = "java.util.stream.Stream.noneMatch",
            takeIf = { it.arguments.arguments.size == 1 },
            toKotlin = { call, streamType ->
                call.toKotlinCollector("kotlin.collections.none", streamType)
            }
        ),
        CollectStreamKindImpl(
            javaFqName = "java.util.stream.Stream.forEach",
            takeIf = { it.arguments.arguments.size == 1 },
            toKotlin = { call, streamType ->
                call.toKotlinCollector("kotlin.collections.forEach", streamType)
            }
        )
    ).groupBy { it.javaFqName }

    private fun JKExpression.toKotlinCollector(
        kotlinFqName: String,
        streamType: JKType
    ) =
        JKKtCallExpressionImpl(
            provideMethodSymbol(kotlinFqName),
            arguments = safeAs<JKMethodCallExpression>()
                ?.arguments
                ?.detached()
                ?.cleanFunctionalTypeLambdaArguments()
                ?: JKArgumentListImpl()
        )
}