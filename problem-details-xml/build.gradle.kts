// `xmlutil` is `implementation`, not `api`: no xmlutil type appears in a public signature or as a
// thrown type — the codec wraps XmlException in SerializationException — so a consumer's own
// xmlutil version cannot break this library's ABI.
//
// The serialization plugin serves `commonTest` alone; nothing in `commonMain` is `@Serializable`.
// This module is not published yet, hence no `rfc9457.published`.
plugins {
    id("rfc9457.kmp-library")
    id("rfc9457.kmp-serialization")
}

kotlin {
    sourceSets.getByName("commonMain").dependencies {
        api(project(":problem-details-core"))
        implementation(libs.xmlutil.core)
    }
    // Already exposed transitively via `api(project(":problem-details-core"))` above; declared to
    // document that the round-trip test depends on it directly.
    sourceSets.getByName("commonTest").dependencies {
        implementation(libs.kotlinx.serialization.json)
    }
}

// This module's root package; see `problem-details-core` for why it is declared per module.
tasks.named<Jar>("jvmJar") {
    manifest { attributes("Automatic-Module-Name" to "io.github.ilyankin.rfc9457.xml") }
}
