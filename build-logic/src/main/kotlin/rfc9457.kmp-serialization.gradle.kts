// Bare id, not `alias(libs.plugins.kotlin.serialization)`: a versioned request gets its own
// classloader scope and loads a second copy of KGP, which Gradle warns "may break the build".
// Resolved from build-logic's shared classpath instead.
plugins {
    id("org.jetbrains.kotlin.plugin.serialization")
}
