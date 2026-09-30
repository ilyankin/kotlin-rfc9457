// A Maven BOM: POM packaging, a `dependencyManagement` section and no code. `java-platform` is the
// Gradle side of the same thing, published with Gradle module metadata so `platform(...)` works too.
plugins {
    `java-platform`
    id("rfc9457.maven-publication")
}

publishing {
    publications.register<MavenPublication>("bom") {
        from(components["javaPlatform"])
    }
}
