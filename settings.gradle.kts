pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "itcabs"
include(":app")
// M2 re-platform onto the backend API (ADR-0007). Pure-JVM modules for the auth data
// vertical; Android modules (:app rebuild, :feature:*, Room in :data) land in later slices.
include(":domain")
include(":core:network")
include(":core:designsystem")
include(":core:database")
include(":data")
include(":feature:auth")
include(":feature:dispatch")
