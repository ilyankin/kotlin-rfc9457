pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

// Artifacts under test come from `publishToMavenLocal`, everything else from Central. The content
// filter keeps a stale local copy of an unrelated library from leaking into the check.
dependencyResolutionManagement {
    repositories {
        mavenLocal { content { includeGroup("io.github.ilyankin") } }
        mavenCentral()
    }
    versionCatalogs {
        create("libs") { from(files("../../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "consumer-smoke-gradle-jvm"
