plugins {
    id("org.jetbrains.dokka")
}

// Root-only: collects the module outputs into one site (`dokka(project(…))` in the root build
// file). Aggregation is also what makes cross-module links resolve. `[Problem]` written in
// `problem-details-ktor` has nothing to point at in a standalone publication.
//
// No `includes`: the landing page lists every module with the opening paragraph of its README.
dokka {
    moduleName.set(rootProject.name)
    moduleVersion.set(project.version.toString())

    dokkaPublications.configureEach {
        failOnWarning.set(true)
    }

    // The published site is built from a release tag, and each release is archived on the
    // `docs-archive` branch. `docs.yml` checks that branch out and passes its path in, so the new
    // site carries every archived version under `older/` behind a version switcher. Without the
    // property, as in CI and locally, the switcher lists the current version alone.
    pluginsConfiguration {
        versioning {
            version.set(project.version.toString())
            olderVersionsDir.set(
                providers
                    .gradleProperty("rfc9457.docs.olderVersionsDir")
                    .map { layout.projectDirectory.dir(it) },
            )
        }
    }
}

// Only here, not in the modules: the switcher belongs to the aggregated site, and a module's
// `-javadoc.jar` has no other versions to switch to. No version: DGP supplies its own.
dependencies {
    dokkaHtmlPlugin("org.jetbrains.dokka:versioning-plugin")
}
