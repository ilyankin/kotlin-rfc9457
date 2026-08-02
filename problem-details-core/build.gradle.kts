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

// JPMS: without this the module name is derived from the jar file name (`problem.details.core.jvm`)
// and moves with any rename. It is part of the API — adding it is additive, changing it later breaks
// every `requires` — and it must equal this module's root package. Declared per module rather than
// derived in the convention plugin so that a module whose package stops matching its artifact name
// cannot silently pick up a wrong name.
tasks.named<Jar>("jvmJar") {
    manifest { attributes("Automatic-Module-Name" to "io.github.ilyankin.rfc9457") }
}
