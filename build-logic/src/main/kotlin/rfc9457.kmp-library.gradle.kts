import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import java.net.URI

plugins {
    id("org.jetbrains.kotlin.multiplatform")

    id("org.jetbrains.dokka")
}

// No `libs` accessor exists inside a precompiled script plugin, so it's looked up by type instead.
// The accessor class reaches the classpath via build-logic/build.gradle.kts.
val libs = the<LibrariesForLibs>()

kotlin {
    explicitApi()

    jvm()
    jvmToolchain(17)

    // Dumps the public API to `api/*.api`, checked by `check`, so any accidental widening is visible
    // in a diff before 1.0 freezes it.
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

// `rfc9457.published` packs this output into the `-javadoc.jar`, which javadoc.io then serves
// directly at javadoc.io/doc/<group>/<artifact>.
dokka {
    moduleName.set(project.name)
    moduleVersion.set(project.version.toString())

    dokkaSourceSets.configureEach {
        includes.from(project.layout.projectDirectory.file("README.md"))

        documentedVisibilities.set(setOf(VisibilityModifier.Public))

        // Turns undocumented public declarations into warnings, which `failOnWarning` below turns
        // into a failed build. Not wired into `check`: Dokka needs network access to resolve
        // external links, and `build` has to keep working offline. It runs on the
        // publishing path instead — `javadocJar` depends on it.
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
    }

    // `@sample` bodies, compiled and run out of `commonTest` so a renamed API breaks the build
    // instead of silently rotting an example. Attached to `commonMain` alone, not through
    // `configureEach` — Dokka refuses to start if two source sets claim the same sample root.
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
