// Requested by bare id, not `alias(libs.plugins.kotlin.serialization)`: a versioned alias gives
// its module a separate classloader scope, loading a second copy of KGP alongside it — Gradle
// warns "may break the build". Resolving it from build-logic's shared classpath instead avoids the duplicate load
plugins {
    id("org.jetbrains.kotlin.plugin.serialization")
}
