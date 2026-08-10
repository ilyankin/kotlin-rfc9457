import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import java.net.URI

plugins {
    id("org.jetbrains.kotlin.multiplatform")

    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlinx.kover")
}

// No `libs` accessor inside a precompiled script plugin; looked up by type instead.
val libs = the<LibrariesForLibs>()

kotlin {
    explicitApi()

    jvm()
    jvmToolchain(17)

    // Dumps the public API to `api/*.api`, checked by `check`, so accidental widening shows in a diff.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()

    sourceSets.getByName("commonTest").dependencies {
        implementation(libs.kotest.framework.engine)
        implementation(libs.kotest.assertions.core)
    }

    sourceSets.getByName("jvmTest").dependencies {
        implementation(libs.kotest.runner.junit5)
    }
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

// `rfc9457.published` packs this output into the `-javadoc.jar` that javadoc.io serves.
dokka {
    moduleName.set(project.name)
    moduleVersion.set(project.version.toString())

    dokkaSourceSets.configureEach {
        includes.from(project.layout.projectDirectory.file("README.md"))

        documentedVisibilities.set(setOf(VisibilityModifier.Public))

        // With `failOnWarning` below, an undocumented public declaration fails the build. Kept off
        // `check` because Dokka needs network access for external links and `build` must work
        // offline; it runs on the publishing path, where `javadocJar` depends on it.
        reportUndocumented.set(true)

        skipDeprecated.set(false)

        sourceLink {
            localDirectory.set(project.layout.projectDirectory.dir("src"))
            remoteUrl.set(URI("https://github.com/ilyankin/kotlin-rfc9457/blob/main/${project.name}/src"))
            remoteLineSuffix.set("#L")
        }

        externalDocumentationLinks.register("kotlinx-serialization") {
            url("https://kotlinlang.org/api/kotlinx.serialization/")
            packageListUrl("https://kotlinlang.org/api/kotlinx.serialization/package-list")
        }

        // A standalone publication has nothing to resolve `[Problem]` against, so KDoc in the
        // dependent modules pointed nowhere. That's visible on javadoc.io, not on the aggregated
        // site. The package list carries locations relative to the site root, so links land on the
        // published aggregate. An unreachable list is not an error: links stay plain text, as they
        // already were. Core is skipped; its own types are local.
        if (project.name != "problem-details-core") {
            externalDocumentationLinks.register("problem-details-core") {
                url("https://ilyankin.github.io/kotlin-rfc9457/")
                packageListUrl("https://ilyankin.github.io/kotlin-rfc9457/problem-details-core/package-list")
            }
        }
    }

    // `@sample` bodies live in `commonTest`, so a renamed API breaks the build instead of rotting an
    // example. Attached to `commonMain` alone: Dokka refuses to start if two source sets claim the
    // same sample root.
    dokkaSourceSets.named("commonMain") {
        samples.from(
            project.layout.projectDirectory
                .dir("src/commonTest/kotlin/io/github/ilyankin/rfc9457/samples"),
        )
    }

    dokkaPublications.configureEach {
        failOnWarning.set(true)
    }
}
