plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    testCompile(projectTests(":compiler"))
}

sourceSets {
    "main" { }
    "test" { projectDefault() }
}

projectTest {
    workingDir = rootDir
}

val generateSpecTests by generator("org.jetbrains.kotlin.spec.tasks.GenerateSpecTestsKt")

val generateSpecTestData by generator("org.jetbrains.kotlin.spec.tasks.GenerateSpecTestDataKt")

val printSpecTestsStatistic by generator("org.jetbrains.kotlin.spec.tasks.PrintSpecTestsStatisticKt")

val generateJsonTestsMap by generator("org.jetbrains.kotlin.spec.tasks.GenerateJsonTestsMapKt")
