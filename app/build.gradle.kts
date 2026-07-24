// :app — the ITCABS Android host. Re-platformed onto the backend API (M2): no Firebase,
// Hilt DI, Compose UI, talks to the modules (:feature:*, :data, :core:*). The old
// Firestore-direct sources are preserved under app/_legacy_firebase/ (not compiled).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    id("com.google.firebase.appdistribution")
}

android {
    namespace = "com.itcabs"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.itcabs"
        minSdk = 24
        targetSdk = 34
        // Override per release so Play accepts updates: -Pitcabs.versionCode=5
        versionCode = (findProperty("itcabs.versionCode") as String?)?.toInt() ?: 2
        versionName = (findProperty("itcabs.versionName") as String?) ?: "0.2"
        // Debug default: emulator → host loopback for the local dev backend.
        buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8081/\"")
        // Supabase Auth. The anon key is a publishable client key (RLS-protected), safe to embed.
        buildConfigField("String", "SUPABASE_URL", "\"https://wjorulwjpjgpeudecjwn.supabase.co\"")
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Indqb3J1bHdqcGpncGV1ZGVjanduIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODQ3OTA3MjAsImV4cCI6MjEwMDM2NjcyMH0.-2UBgqBBKYTWcV4Jzo7PMKIdjbdsa4oniDsxYk3cT40\"",
        )
        // Google sign-in: the Web OAuth client id (public) used by Credential Manager to fetch an ID token.
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"570929271382-11395nr8vthv99hb5r6clnoqjv1veqfu.apps.googleusercontent.com\"",
        )
        // Google Maps/Places key slot — empty until billing is enabled. Supply at build time:
        //   -Pitcabs.mapsApiKey=AIza...   (map view + precise geocoding light up when non-empty)
        buildConfigField(
            "String",
            "MAPS_API_KEY",
            "\"${(findProperty("itcabs.mapsApiKey") as String?) ?: ""}\"",
        )
        // Same key for the Maps SDK manifest meta-data (never committed; supplied at build time).
        manifestPlaceholders["MAPS_API_KEY"] = (findProperty("itcabs.mapsApiKey") as String?) ?: ""
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
    signingConfigs {
        // Real upload keystore, supplied out-of-band (never committed). Set itcabs.keystore (or the
        // ITCABS_KEYSTORE env var) + the matching password/alias props to sign a Play-ready build.
        create("upload") {
            val storePath = System.getenv("ITCABS_KEYSTORE") ?: findProperty("itcabs.keystore") as String?
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = System.getenv("ITCABS_KEYSTORE_PW") ?: findProperty("itcabs.keystorePw") as String?
                keyAlias = System.getenv("ITCABS_KEY_ALIAS") ?: findProperty("itcabs.keyAlias") as String?
                keyPassword = System.getenv("ITCABS_KEY_PW") ?: findProperty("itcabs.keyPw") as String?
            }
        }
    }
    buildTypes {
        release {
            // R8 shrink + obfuscate for a smaller, harder-to-reverse Play build. Keep rules in
            // proguard-rules.pro cover serialization/Retrofit/Hilt/Firebase.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sign with the upload keystore when provided; otherwise debug-sign so testers can sideload.
            val hasUploadKey = (System.getenv("ITCABS_KEYSTORE") ?: findProperty("itcabs.keystore") as String?) != null
            signingConfig = if (hasUploadKey) signingConfigs.getByName("upload") else signingConfigs.getByName("debug")
            // Hosted backend URL. Set at build time: -Pitcabs.baseUrl=https://itcabs-backend.onrender.com/
            // or the ITCABS_BASE_URL env var. Falls back to a placeholder so a bare build still compiles.
            // Default to the live pilot backend so a bare `assembleRelease` still works; override
            // with -Pitcabs.baseUrl=… or the ITCABS_BASE_URL env var for other environments.
            val releaseBaseUrl = (findProperty("itcabs.baseUrl") as String?)
                ?: System.getenv("ITCABS_BASE_URL")
                ?: "https://itcabs.onrender.com/"
            buildConfigField("String", "BASE_URL", "\"$releaseBaseUrl\"")

            // Firebase App Distribution: upload this build to testers with
            //   ./gradlew assembleRelease appDistributionUploadRelease -Pitcabs.baseUrl=...
            // Auth via the gitignored service-account key (also usable via FIREBASE_CREDENTIALS env).
            firebaseAppDistribution {
                appId = "1:570929271382:android:567a67a55e70f8713d21f7"
                serviceCredentialsFile = System.getenv("FIREBASE_CREDENTIALS")
                    ?: rootProject.file("backend/secrets/firebase-admin.json").path
                groups = "testers"
                releaseNotes = "ITCABS pilot build"
            }
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
    implementation(libs.firebase.auth)       // phone-OTP KYC
    implementation(libs.firebase.messaging)  // FCM push
    // firestore/storage removed — the port uses the backend API + Supabase Storage, not Firebase.

    testImplementation(libs.junit)
}
