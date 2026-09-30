import rfc9457.build.PublishedTargets

plugins {
    id("rfc9457.bom")
}

// Every module that applies `rfc9457.published`. A new published module needs a line here, as it
// does in the root build file.
val libraryModules =
    listOf(
        "problem-details-core",
        "problem-details-xml",
        "problem-details-ktor",
        "problem-details-ktor-xml",
        "problem-details-ktor-client",
        "problem-details-ktor-client-xml",
        "problem-details-ktor-validation",
        "problem-details-ktor-openapi",
        "problem-details-ktor-openapi-xml",
    )

// The platform artifacts are listed as well as the root ones. A `-jvm` POM depends on the `-jvm`
// artifacts of its neighbours directly, so a Maven build that managed only the root coordinates
// could still pull two versions of `problem-details-core-jvm` through two modules.
dependencies {
    constraints {
        for (module in libraryModules) {
            api(project(":$module"))
            for (target in PublishedTargets.names) {
                api("${project.group}:${PublishedTargets.artifactId(module, target)}:${project.version}")
            }
        }
    }
}
