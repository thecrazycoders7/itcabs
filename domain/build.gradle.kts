// :domain — pure Kotlin, framework-free entities + repository interfaces + use cases.
// No Android, no Firestore, no Retrofit here (ADR-0007). Portable and unit-testable on the JVM.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.20")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
