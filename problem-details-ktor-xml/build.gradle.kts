// No `rfc9457.published`: like `problem-details-xml`, this module ships at 0.2.0 rather than 0.1.0.
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
