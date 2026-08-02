// No dependency on `problem-details-xml`; that is the point of the split.
plugins {
    id("rfc9457.kmp-library")
    id("rfc9457.published")
}

kotlin {
    sourceSets.getByName("commonMain").dependencies {
        api(project(":problem-details-core"))
        api(libs.ktor.server.core)
        api(libs.ktor.server.status.pages)
        api(libs.ktor.server.content.negotiation)
    }
    sourceSets.getByName("commonTest").dependencies {
        implementation(libs.ktor.server.test.host)
    }
    // The library logs through Ktor's `Logger`, which on JVM is `org.slf4j.Logger`. Asserting on what
    // it emits needs a real backend, so those tests live in `jvmTest` rather than `commonTest`.
    sourceSets.getByName("jvmTest").dependencies {
        implementation(libs.logback.classic)
    }
}

// This module's root package; see `problem-details-core` for why it is declared per module.
tasks.named<Jar>("jvmJar") {
    manifest { attributes("Automatic-Module-Name" to "io.github.ilyankin.rfc9457.ktor") }
}

// Dokka knows stdlib and coroutines, not Ktor, so `[HttpStatusCode]` and friends rendered as plain
// text — an unresolved link is a dead reference, not a build failure. Not in the convention plugin:
// modules without a Ktor dependency would fetch this package list for nothing.
dokka {
    dokkaSourceSets.configureEach {
        externalDocumentationLinks.register("ktor") {
            url("https://api.ktor.io/")
            packageListUrl("https://api.ktor.io/package-list")
        }
    }
}
