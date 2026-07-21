// :feature:dispatch — driver feed (browse + claim) and coordinator create-job screens,
// driven by DispatchRepository. Android library; runs via :app.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.itcabs.feature.dispatch"
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:designsystem"))

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
