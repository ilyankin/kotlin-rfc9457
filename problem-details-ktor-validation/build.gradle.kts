plugins {
    id("rfc9457.kmp-library")
    // For `commonTest` only: nothing in `commonMain` is `@Serializable`, but the Dokka sample for
    // `decodeValidationReason` builds a typed `errors[]` entry and needs the compiler plugin.
    id("rfc9457.kmp-serialization")
    id("rfc9457.published")
}

kotlin {
    sourceSets.getByName("commonMain").dependencies {
        api(project(":problem-details-ktor"))
        api(libs.ktor.server.request.validation)
    }
    sourceSets.getByName("commonTest").dependencies {
        implementation(libs.ktor.server.test.host)
        // Samples validate a typed DTO, and `ProblemJsonConverter` deserializes nothing but
        // `Problem`. Receiving the DTO needs an ordinary `json()` converter beside it. Test-only:
        // the library itself never reads an application's own body types.
        implementation(libs.ktor.serialization.kotlinx.json)
    }
}

// This module's root package; see `problem-details-core` for why it is declared per module.
tasks.named<Jar>("jvmJar") {
    manifest { attributes("Automatic-Module-Name" to "io.github.ilyankin.rfc9457.ktor.validation") }
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
