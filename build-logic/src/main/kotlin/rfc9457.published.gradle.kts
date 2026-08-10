import org.jetbrains.dokka.gradle.tasks.DokkaGenerateTask
import rfc9457.build.RewriteRootPomToJvmRedirect

plugins {
    `maven-publish`
    signing
}

// Maven Central rejects an artifact without a -javadoc.jar and DGP v2 builds none itself. Carries
// Dokka's HTML output rather than its Javadoc-format output, which is still Alpha; javadoc.io serves
// whatever this jar holds.
val dokkaHtml = tasks.named<DokkaGenerateTask>("dokkaGeneratePublicationHtml")

val javadocJar =
    tasks.register<Jar>("javadocJar") {
        description = "Assembles the javadoc JAR published to Maven repositories."
        archiveClassifier.set("javadoc")
        from(dokkaHtml.flatMap { it.outputDirectory })
    }

// Captured here, not read inside `withXml`. That action runs at execution time, so whatever it
// closes over is serialized into the configuration cache, and `project` cannot be. The failure shows
// only on `publish*` tasks, never on `build`.
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

        // A multiplatform root publication carries Kotlin metadata, not JVM classes, so plain
        // coordinates would hand a consumer an empty jar. Rewritten to packaging `pom` plus a
        // compile-scoped dependency on the `-jvm` artifact so they resolve transitively.
        // This is irreversible once published: changing it breaks everyone who wrote either form.
        if (name == "kotlinMultiplatform") {
            pom.withXml(RewriteRootPomToJvmRedirect(artifactGroup, jvmArtifactId, artifactVersion))
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

    // Both publications attach the same Javadoc jar, so their sign tasks write the same `.asc` path
    // and Gradle refuses the build without explicit ordering. Invisible until a real key is present.
    tasks.withType<AbstractPublishToMaven>().configureEach {
        dependsOn(tasks.withType<Sign>())
    }
}
