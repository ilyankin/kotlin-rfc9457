pluginManagement {
    // Convention plugins live in an included build, so they must be resolvable before any project
    // build script is evaluated.
    includeBuild("build-logic")

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Provisions the JDK 17 toolchain on machines that don't have one installed, and resolves the
    // daemon JVM declared in `gradle/gradle-daemon-jvm.properties`.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"

    // Uploads to the Central Portal via its own bundle-zip API, which `maven-publish` can't do
    // alone. Chosen over `com.vanniktech.maven.publish` because it only uploads — POM, javadoc and
    // signing stay ours (docs/release-strategy.md). It applies itself to every project with
    // `maven-publish`, so which modules ship is still decided by `rfc9457.published`.
    //
    // Version is a literal because a settings `plugins { }` block runs before
    // `dependencyResolutionManagement`, so the catalog isn't available yet — same as foojay above.
    id("com.gradleup.nmcp.settings") version "1.6.1"
}

// Read here rather than handed to nmcp as providers: its settings plugin captures its config into a
// `gradle.lifecycle.beforeProject` action, which Gradle isolates per project, and an environment
// provider fails to isolate there. Reading them now still registers both as configuration-cache
// inputs.
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
        // Left unset without credentials — nmcp's own "missing credentials" error is clearer than
        // an empty string reaching the Portal. `nmcpPublishAggregationToMavenLocal` rehearses the
        // whole bundle without them.
        if (portalUsername != null) username = portalUsername
        if (portalPassword != null) password = portalPassword

        // Waits for a human to press Publish in the Portal — a Central release can't be undone, and
        // `AUTOMATIC` would release the moment validation passed. Revisit once one release has gone
        // through by hand.
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
)
