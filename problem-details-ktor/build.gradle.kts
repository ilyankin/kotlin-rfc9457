// No dependency on `problem-details-xml`; that is the point of the split.
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
}
