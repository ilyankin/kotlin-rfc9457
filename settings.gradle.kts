pluginManagement {
    includeBuild("build-logic")

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Versions stay literals: a settings `plugins { }` block is evaluated before
    // `dependencyResolutionManagement`, so the version catalog does not exist yet.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradleup.nmcp.settings") version "1.6.1"
}

// Read into strings rather than passed as providers: nmcp captures its config into a
// `gradle.lifecycle.beforeProject` action that Gradle isolates per project, where a provider fails
// to serialize. Both remain configuration-cache inputs either way.
val portalUsername: String? =
    providers
        .gradleProperty("mavenCentralUsername")
        .orElse(providers.environmentVariable("MAVEN_CENTRAL_USERNAME"))
        .orNull
val portalPassword: String? =
    providers
        .gradleProperty("mavenCentralPassword")
        .orElse(providers.environmentVariable("MAVEN_CENTRAL_PASSWORD"))
        .orNull

nmcpSettings {
    centralPortal {
        // Left unset when absent, so nmcp reports missing credentials instead of the Portal
        // rejecting an empty string.
        if (portalUsername != null) username = portalUsername
        if (portalPassword != null) password = portalPassword

        // A Central release is irreversible, so a human presses Publish; `AUTOMATIC` would release
        // the moment validation passed.
        publishingType = "USER_MANAGED"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kotlin-rfc9457"

include(
    "problem-details-core",
    "problem-details-xml",
    "problem-details-ktor",
    "problem-details-ktor-xml",
    "problem-details-ktor-client",
)
