kotlin {
    sourceSets.getByName("commonMain").dependencies {
        api(project(":problem-details-ktor"))
        api(project(":problem-details-xml"))
    }
    sourceSets.getByName("commonTest").dependencies {
        implementation(libs.ktor.server.test.host)
    }
}
