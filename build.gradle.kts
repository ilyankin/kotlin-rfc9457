// Module configuration lives in `build-logic`. The root project aggregates what needs every
// module's output in one place: documentation, and now coverage.
plugins {
    id("rfc9457.docs-aggregation")
    alias(libs.plugins.kover)
}

dependencies {
    dokka(project(":problem-details-core"))
    dokka(project(":problem-details-ktor"))
    dokka(project(":problem-details-xml"))
    dokka(project(":problem-details-ktor-xml"))
    dokka(project(":problem-details-ktor-client"))

    // Kover is also applied per module (`rfc9457.kmp-library`, so each module's own tests get
    // instrumented); this declares the root as the merging module that combines all five into one
    // report. `:koverXmlReport` here is what a Codecov upload step will consume.
    kover(project(":problem-details-core"))
    kover(project(":problem-details-ktor"))
    kover(project(":problem-details-xml"))
    kover(project(":problem-details-ktor-xml"))
    kover(project(":problem-details-ktor-client"))
}
