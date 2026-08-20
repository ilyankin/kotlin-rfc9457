pluginManagement {
    includeBuild("build-logic")

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradleup.nmcp.settings") version "1.6.1"
}

// Read into strings, not passed as providers: nmcp captures its config into a
// `gradle.lifecycle.beforeProject` action that Gradle isolates per project, where a provider fails
// to serialize. Both remain configuration-cache inputs.
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
        if (portalUsername != null) username = portalUsername
        if (portalPassword != null) password = portalPassword

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
    "problem-details-ktor-client-xml",
    "problem-details-ktor-validation",
    "problem-details-ktor-openapi",
    "problem-details-ktor-openapi-xml",
)
