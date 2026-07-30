pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Provisions the JDK 17 toolchain on machines that don't have one installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
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
)
