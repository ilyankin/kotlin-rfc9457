// No dependency on `problem-details-ktor` (server) or any XML module — this module only needs the
// wire format, not how the other side of the connection is built.
plugins {
    id("rfc9457.kmp-library")
    id("rfc9457.published")
}

kotlin {
    sourceSets.getByName("commonMain").dependencies {
        api(project(":problem-details-core"))
        api(libs.ktor.client.core)
    }
    sourceSets.getByName("commonTest").dependencies {
        implementation(libs.ktor.client.mock)
        // Test-only: proves this module decodes what problem-details-ktor actually emits, not just
        // hand-built mock bytes.
        implementation(project(":problem-details-ktor"))
        implementation(libs.ktor.server.test.host)
    }
}

// This module's root package; see `problem-details-core` for why it is declared per module.
tasks.named<Jar>("jvmJar") {
    manifest { attributes("Automatic-Module-Name" to "io.github.ilyankin.rfc9457.ktor.client") }
}

// Same reason as in `problem-details-ktor`: this module's KDoc names Ktor types.
dokka {
    dokkaSourceSets.configureEach {
        externalDocumentationLinks.register("ktor") {
            url("https://api.ktor.io/")
            packageListUrl("https://api.ktor.io/package-list")
        }
    }
}
