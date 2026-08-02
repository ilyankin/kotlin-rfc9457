// Module configuration lives in `build-logic`. The one thing that belongs to the root is
// documentation aggregation: the modules become one site, and cross-module KDoc links resolve.
plugins {
    id("rfc9457.docs-aggregation")
}

dependencies {
    dokka(project(":problem-details-core"))
    dokka(project(":problem-details-ktor"))
    dokka(project(":problem-details-xml"))
    // dokka(project(":problem-details-ktor-xml"))
}
