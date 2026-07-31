plugins {
    id("rfc9457.kmp-library")
    id("rfc9457.kmp-serialization")
    id("rfc9457.published")
}

kotlin {
    sourceSets.getByName("commonMain").dependencies {
        api(libs.kotlinx.serialization.json)
    }
}
