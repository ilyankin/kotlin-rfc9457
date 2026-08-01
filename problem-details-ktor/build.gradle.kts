// No dependency on `problem-details-xml`; that is the point of the split.
plugins {
    id("rfc9457.kmp-library")
    id("rfc9457.published")
}

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
    // The library logs through Ktor's `Logger`, which on JVM is `org.slf4j.Logger`. Asserting on what
    // it emits needs a real backend, so those tests live in `jvmTest` rather than `commonTest`.
    sourceSets.getByName("jvmTest").dependencies {
        implementation(libs.logback.classic)
    }
}
