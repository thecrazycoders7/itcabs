// :app — the ITCABS Android host. Re-platformed onto the backend API (M2): no Firebase,
// Hilt DI, Compose UI, talks to the modules (:feature:*, :data, :core:*). The old
// Firestore-direct sources are preserved under app/_legacy_firebase/ (not compiled).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.itcabs"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.itcabs"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.2"
        // Debug default: emulator → host loopback for the local dev backend.
        buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8081/\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release {
            isMinifyEnabled = false
            // Point release builds at the hosted backend before shipping (see docs/DEPLOY.md).
            buildConfigField("String", "BASE_URL", "\"https://REPLACE-WITH-HOSTED-URL/\"")
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":core:network"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:database"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:dispatch"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.security.crypto)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)

    testImplementation(libs.junit)
}
