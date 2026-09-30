// What every artifact sent to Maven Central needs, whatever it contains: POM metadata and
// signatures. Applied through `rfc9457.published` for the library modules and `rfc9457.bom` for the
// BOM; applying either is what makes a module ship.
plugins {
    `maven-publish`
    signing
}

val moduleName = project.name

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set(moduleName)
            description.set(
                "RFC 9457 Problem Details for HTTP APIs — ${moduleName.removePrefix("problem-details-")} module",
            )
            url.set("https://github.com/ilyankin/kotlin-rfc9457")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("ilyankin")
                    name.set("ilyankin")
                    url.set("https://github.com/ilyankin")
                }
            }
            scm {
                url.set("https://github.com/ilyankin/kotlin-rfc9457")
                connection.set("scm:git:https://github.com/ilyankin/kotlin-rfc9457.git")
                developerConnection.set("scm:git:ssh://git@github.com/ilyankin/kotlin-rfc9457.git")
            }
        }
    }
}

// Conditional so `publishToMavenLocal` works without a key. Read through `providers` so the
// configuration cache tracks them as inputs.
val signingKey =
    providers
        .gradleProperty("signingKey")
        .orElse(providers.environmentVariable("SIGNING_KEY"))
val signingPassword =
    providers
        .gradleProperty("signingPassword")
        .orElse(providers.environmentVariable("SIGNING_PASSWORD"))

if (signingKey.isPresent && signingPassword.isPresent) {
    signing {
        useInMemoryPgpKeys(signingKey.get(), signingPassword.get())
        sign(publishing.publications)
    }

    // A multiplatform module's publications all attach the same Javadoc jar, so their sign tasks
    // write the same `.asc` path and Gradle refuses the build without explicit ordering. Invisible
    // until a real key is present.
    tasks.withType<AbstractPublishToMaven>().configureEach {
        dependsOn(tasks.withType<Sign>())
    }
}
