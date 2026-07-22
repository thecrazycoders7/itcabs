// :domain — pure Kotlin, framework-free entities + repository interfaces + use cases.
// No Android, no Firestore, no Retrofit here (ADR-0007). Portable and unit-testable on the JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
