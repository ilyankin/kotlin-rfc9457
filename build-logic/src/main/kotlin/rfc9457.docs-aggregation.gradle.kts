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
}
