// :core:network — Retrofit/OkHttp/kotlinx.serialization client for the ITCABS backend.
// Pure JVM (no Android framework needed for the HTTP client), so it's unit-testable.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    // Public surface (AuthApi returns retrofit2.Response; AuthInterceptor is an okhttp Interceptor),
    // so consumers get these transitively.
    api("com.squareup.retrofit2:retrofit:2.11.0")
    api("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.20")
}
