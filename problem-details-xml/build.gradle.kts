// No kotlinx.serialization, no Ktor. `xmlutil` is `implementation`, not `api`: no xmlutil type
// appears in a public signature, which is what stops a consumer's own xmlutil version from
// being able to break this library's ABI.
kotlin {
    sourceSets.getByName("commonMain").dependencies {
        api(project(":problem-details-core"))
        implementation(libs.xmlutil.core)
    }
}
