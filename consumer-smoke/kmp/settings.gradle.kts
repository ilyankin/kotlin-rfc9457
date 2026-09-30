pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

// Artifacts under test come from `publishToMavenLocal`, everything else from Central.
dependencyResolutionManagement {
    repositories {
        mavenLocal { content { includeGroup("io.github.ilyankin") } }
        mavenCentral()
    }
    versionCatalogs {
        create("libs") { from(files("../../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "consumer-smoke-kmp"
