plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(pluginMarker(libs.plugins.kotlin.multiplatform))
    implementation(pluginMarker(libs.plugins.kotlin.serialization))
    implementation(pluginMarker(libs.plugins.dokka))

    // Precompiled script plugins get no generated `libs` accessor (gradle/gradle#15383). This puts
    // the generated accessor classes on the classpath so convention plugins can look them up via
    // `the<LibrariesForLibs>()` — see the comment there.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

// A convention plugin can only apply a plugin on its own compile classpath so
// deriving the marker from the catalog keeps the Kotlin version declared once, in `gradle/libs.versions.toml`.
fun pluginMarker(plugin: Provider<PluginDependency>): Provider<String> =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
