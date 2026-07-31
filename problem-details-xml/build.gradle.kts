// No Ktor. `xmlutil` is `implementation`, not `api` — no xmlutil type appears in a public signature
// or thrown type, so the codec wraps XmlException in SerializationException instead. That's what
// stops a consumer's own xmlutil version from being able to break this library's ABI.
//
// The serialization plugin and the `commonTest` dependency below exist only for
// CrossFormatRoundTripTest, which decodes the RFC's JSON example to compare the two codecs — nothing
// in `commonMain` is `@Serializable`, so the plugin generates nothing for the production artifact.
//
// No `rfc9457.published`: docs/release-strategy.md holds the XML half back to 0.2.0.
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
