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
        versionCode = 1
        versionName = "0.2"
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
            // ponytail: pilot signs the release with the debug key so testers can sideload it.
            // Generate a proper upload keystore before a Play release.
            signingConfig = signingConfigs.getByName("debug")
            // Hosted backend URL. Set at build time: -Pitcabs.baseUrl=https://itcabs-backend.onrender.com/
            // or the ITCABS_BASE_URL env var. Falls back to a placeholder so a bare build still compiles.
            val releaseBaseUrl = (findProperty("itcabs.baseUrl") as String?)
                ?: System.getenv("ITCABS_BASE_URL")
                ?: "https://REPLACE-WITH-HOSTED-URL/"
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
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
}
