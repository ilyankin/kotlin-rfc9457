import org.jetbrains.dokka.gradle.tasks.DokkaGenerateTask
import rfc9457.build.RewriteRootPomToJvmRedirect

plugins {
    id("rfc9457.maven-publication")
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

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifact(javadocJar)

        // A multiplatform root publication carries Kotlin metadata, not JVM classes, so plain
        // coordinates would hand a consumer an empty jar. Rewritten to packaging `pom` plus a
        // compile-scoped dependency on the `-jvm` artifact so they resolve transitively.
        // This is irreversible once published: changing it breaks everyone who wrote either form.
        if (name == "kotlinMultiplatform") {
            pom.withXml(RewriteRootPomToJvmRedirect(artifactGroup, jvmArtifactId, artifactVersion))
        }
    }
}
