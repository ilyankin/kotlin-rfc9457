// Module configuration lives in `build-logic`. The one thing that belongs to the root is
// documentation aggregation: the four modules become one site, and cross-module KDoc links resolve.
plugins {
    id("rfc9457.docs-aggregation")
}

dependencies {
    dokka(project(":problem-details-core"))
    dokka(project(":problem-details-ktor"))
    // Included though unpublished: the code exists and is documented, and the site is where the
    // decision to release it gets made. Their READMEs say they are not on Maven Central.
    dokka(project(":problem-details-xml"))
    dokka(project(":problem-details-ktor-xml"))
}
