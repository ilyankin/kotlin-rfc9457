import org.jetbrains.dokka.gradle.tasks.DokkaGenerateTask
import rfc9457.build.RewriteRootPomToJvmRedirect

plugins {
    `maven-publish`
    signing
}

// Maven Central rejects an artifact without a -javadoc.jar, and DGP v2 doesn't build one itself —
// so this is that task. It carries Dokka's HTML output under the `javadoc` classifier, not Dokka's
// Javadoc-format output: `org.jetbrains.dokka-javadoc` is still Alpha with no guarantee that Javadoc
// HTML consumers can read it, while javadoc.io serves whatever this jar holds and HTML is guaranteed
// to render. Replaces an earlier stub jar holding the README — valid by Central's rules, but it
// would have made javadoc.io/doc/io.github.ilyankin/<artifact> resolve to an empty page.
val dokkaHtml = tasks.named<DokkaGenerateTask>("dokkaGeneratePublicationHtml")

val javadocJar =
    tasks.register<Jar>("javadocJar") {
        description = "Assembles the javadoc JAR published to Maven repositories."
        archiveClassifier.set("javadoc")
        from(dokkaHtml.flatMap { it.outputDirectory })
    }

// Captured here rather than read inside `withXml` below — that action runs at execution time, so
// anything it closes over gets serialized into the configuration cache, and `project` can't be.
// That failure only ever appeared on `publish*` tasks, so plain `build --configuration-cache` looked
// green while publishing was broken.
val artifactGroup = project.group.toString()
val artifactVersion = project.version.toString()
val jvmArtifactId = "${project.name}-jvm"
val moduleName = project.name

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifact(javadocJar)

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

        // The root publication of a multiplatform project carries Kotlin metadata, not JVM classes — a consumer
        // who writes the plain coordinates gets an empty jar and has to find the `-jvm` suffix the hard way.
        // Rewritten the way kotlinx-serialization and kotlinx-coroutines do it: packaging `pom`
        // plus a compile-scoped dependency on the `-jvm` artifact, so plain coordinates resolve
        // transitively. Switching this later would break everyone who already wrote either form.
        if (name == "kotlinMultiplatform") {
            pom.withXml(RewriteRootPomToJvmRedirect(artifactGroup, jvmArtifactId, artifactVersion))
        }
    }
}

// Conditional so `publishToMavenLocal` still works without a key: supply it via Gradle properties
// (`signingKey`/`signingPassword` in ~/.gradle/gradle.properties) or SIGNING_KEY/SIGNING_PASSWORD
// env vars, which is what CI will use. Read through `providers` so the configuration cache tracks
// them as inputs.
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

    // Both publications attach the same Javadoc jar, so their sign tasks write the same `.asc`
    // path — without explicit ordering Gradle refuses the build outright. Invisible without a real
    // key, so this would have surfaced on the first signed publication rather than any local test.
    tasks.withType<AbstractPublishToMaven>().configureEach {
        dependsOn(tasks.withType<Sign>())
    }
}
