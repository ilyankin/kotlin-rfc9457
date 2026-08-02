// No `rfc9457.published`: like `problem-details-xml`, this module is not released yet.
plugins {
    id("rfc9457.kmp-library")
}

kotlin {
    sourceSets.getByName("commonMain").dependencies {
        api(project(":problem-details-ktor"))
        api(project(":problem-details-xml"))
    }
    sourceSets.getByName("commonTest").dependencies {
        implementation(libs.ktor.server.test.host)
    }
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
