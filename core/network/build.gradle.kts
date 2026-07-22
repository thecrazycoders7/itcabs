// :core:network — Retrofit/OkHttp/kotlinx.serialization client for the ITCABS backend.
// Pure JVM (no Android framework needed for the HTTP client), so it's unit-testable.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // Public surface (AuthApi returns retrofit2.Response; AuthInterceptor is an okhttp Interceptor),
    // so consumers get these transitively.
    api(libs.retrofit)
    api(libs.okhttp)

    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
}
