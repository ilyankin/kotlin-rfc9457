plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(pluginMarker(libs.plugins.kotlin.multiplatform))
    implementation(pluginMarker(libs.plugins.kotlin.serialization))
    implementation(pluginMarker(libs.plugins.dokka))
    implementation(pluginMarker(libs.plugins.kover))

    // Precompiled script plugins get no generated `libs` accessor (gradle/gradle#15383); this puts
    // the accessor classes on the classpath so they can be reached via `the<LibrariesForLibs>()`.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

// A convention plugin can only apply plugins on its own compile classpath. Deriving the marker from
// the catalog keeps every version declared once, in `gradle/libs.versions.toml`.
fun pluginMarker(plugin: Provider<PluginDependency>): Provider<String> =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
