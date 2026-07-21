// :data — DTO↔domain mappers and repository implementations that call :core:network.
// Pure JVM for the API-client slice; gains the Android plugin + Room when the local
// single-source-of-truth cache lands (ADR-0007, next slices).
plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    api(project(":domain"))
    implementation(project(":core:network"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.20")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
