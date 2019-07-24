/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.nj2k.inference.nullability;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TargetBackend;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.TestsPackage}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("nj2k/testData/inference/nullability")
@TestDataPath("$PROJECT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public class NullabilityInferenceTestGenerated extends AbstractNullabilityInferenceTest {
    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, TargetBackend.ANY, testDataFilePath);
    }

    public void testAllFilesPresentInNullability() throws Exception {
        KotlinTestUtils.assertAllTestsPresentByMetadata(this.getClass(), new File("nj2k/testData/inference/nullability"), Pattern.compile("^(.+)\\.kt$"), TargetBackend.ANY, true);
    }

    @TestMetadata("arrayOfArraysOfNull.kt")
    public void testArrayOfArraysOfNull() throws Exception {
        runTest("nj2k/testData/inference/nullability/arrayOfArraysOfNull.kt");
    }

    @TestMetadata("arrayOfNulls.kt")
    public void testArrayOfNulls() throws Exception {
        runTest("nj2k/testData/inference/nullability/arrayOfNulls.kt");
    }

    @TestMetadata("binaryExpressionOperand.kt")
    public void testBinaryExpressionOperand() throws Exception {
        runTest("nj2k/testData/inference/nullability/binaryExpressionOperand.kt");
    }

    @TestMetadata("callExternallyAnnotatedJavaFunction.kt")
    public void testCallExternallyAnnotatedJavaFunction() throws Exception {
        runTest("nj2k/testData/inference/nullability/callExternallyAnnotatedJavaFunction.kt");
    }

    @TestMetadata("classTypeParameters.kt")
    public void testClassTypeParameters() throws Exception {
        runTest("nj2k/testData/inference/nullability/classTypeParameters.kt");
    }

    @TestMetadata("compareWithNull.kt")
    public void testCompareWithNull() throws Exception {
        runTest("nj2k/testData/inference/nullability/compareWithNull.kt");
    }

    @TestMetadata("forcedNullability.kt")
    public void testForcedNullability() throws Exception {
        runTest("nj2k/testData/inference/nullability/forcedNullability.kt");
    }

    @TestMetadata("functionTypeParameterNullability.kt")
    public void testFunctionTypeParameterNullability() throws Exception {
        runTest("nj2k/testData/inference/nullability/functionTypeParameterNullability.kt");
    }

    @TestMetadata("functions.kt")
    public void testFunctions() throws Exception {
        runTest("nj2k/testData/inference/nullability/functions.kt");
    }

    @TestMetadata("ifCondition.kt")
    public void testIfCondition() throws Exception {
        runTest("nj2k/testData/inference/nullability/ifCondition.kt");
    }

    @TestMetadata("javaStream.kt")
    public void testJavaStream() throws Exception {
        runTest("nj2k/testData/inference/nullability/javaStream.kt");
    }

    @TestMetadata("lambdaReturnNull.kt")
    public void testLambdaReturnNull() throws Exception {
        runTest("nj2k/testData/inference/nullability/lambdaReturnNull.kt");
    }

    @TestMetadata("listOfWithNullLiteral.kt")
    public void testListOfWithNullLiteral() throws Exception {
        runTest("nj2k/testData/inference/nullability/listOfWithNullLiteral.kt");
    }

    @TestMetadata("loopIterator.kt")
    public void testLoopIterator() throws Exception {
        runTest("nj2k/testData/inference/nullability/loopIterator.kt");
    }

    @TestMetadata("loops.kt")
    public void testLoops() throws Exception {
        runTest("nj2k/testData/inference/nullability/loops.kt");
    }

    @TestMetadata("notNullCallSequence.kt")
    public void testNotNullCallSequence() throws Exception {
        runTest("nj2k/testData/inference/nullability/notNullCallSequence.kt");
    }

    @TestMetadata("nullAsAssignment.kt")
    public void testNullAsAssignment() throws Exception {
        runTest("nj2k/testData/inference/nullability/nullAsAssignment.kt");
    }

    @TestMetadata("nullAsInitializer.kt")
    public void testNullAsInitializer() throws Exception {
        runTest("nj2k/testData/inference/nullability/nullAsInitializer.kt");
    }

    @TestMetadata("nullLiteral.kt")
    public void testNullLiteral() throws Exception {
        runTest("nj2k/testData/inference/nullability/nullLiteral.kt");
    }

    @TestMetadata("returnNull.kt")
    public void testReturnNull() throws Exception {
        runTest("nj2k/testData/inference/nullability/returnNull.kt");
    }

    @TestMetadata("sequenceOfCallsWIthLambda.kt")
    public void testSequenceOfCallsWIthLambda() throws Exception {
        runTest("nj2k/testData/inference/nullability/sequenceOfCallsWIthLambda.kt");
    }

    @TestMetadata("smartCast.kt")
    public void testSmartCast() throws Exception {
        runTest("nj2k/testData/inference/nullability/smartCast.kt");
    }

    @TestMetadata("spreadExpression.kt")
    public void testSpreadExpression() throws Exception {
        runTest("nj2k/testData/inference/nullability/spreadExpression.kt");
    }

    @TestMetadata("superMethod.kt")
    public void testSuperMethod() throws Exception {
        runTest("nj2k/testData/inference/nullability/superMethod.kt");
    }

    @TestMetadata("typeCast.kt")
    public void testTypeCast() throws Exception {
        runTest("nj2k/testData/inference/nullability/typeCast.kt");
    }

    @TestMetadata("typeParameters.kt")
    public void testTypeParameters() throws Exception {
        runTest("nj2k/testData/inference/nullability/typeParameters.kt");
    }

    @TestMetadata("typeParametersReturnType.kt")
    public void testTypeParametersReturnType() throws Exception {
        runTest("nj2k/testData/inference/nullability/typeParametersReturnType.kt");
    }

    @TestMetadata("typeParametersValueParams.kt")
    public void testTypeParametersValueParams() throws Exception {
        runTest("nj2k/testData/inference/nullability/typeParametersValueParams.kt");
    }

    @TestMetadata("useAsReceiver.kt")
    public void testUseAsReceiver() throws Exception {
        runTest("nj2k/testData/inference/nullability/useAsReceiver.kt");
    }

    @TestMetadata("whileCondition.kt")
    public void testWhileCondition() throws Exception {
        runTest("nj2k/testData/inference/nullability/whileCondition.kt");
    }
}
