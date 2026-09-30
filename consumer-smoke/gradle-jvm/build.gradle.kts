plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

val rfc9457Version = providers.gradleProperty("rfc9457.version").get()

// Versions come from the BOM alone: a module it failed to manage would fail to resolve here.
dependencies {
    implementation(platform("io.github.ilyankin:problem-details-bom:$rfc9457Version"))
    implementation("io.github.ilyankin:problem-details-core")
    implementation("io.github.ilyankin:problem-details-xml")
    implementation("io.github.ilyankin:problem-details-ktor")
    implementation("io.github.ilyankin:problem-details-ktor-client")
}

kotlin { jvmToolchain(17) }

application { mainClass.set("smoke.SmokeKt") }
