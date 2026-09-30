import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val rfc9457Version = providers.gradleProperty("rfc9457.version").get()

// Every target the library publishes, so each platform variant is resolved and compiled against.
kotlin {
    jvm()
    js { nodejs() }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { nodejs() }

    linuxX64()
    linuxArm64()
    mingwX64()
    macosArm64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets.commonMain.dependencies {
        implementation(project.dependencies.platform("io.github.ilyankin:problem-details-bom:$rfc9457Version"))
        implementation("io.github.ilyankin:problem-details-core")
        implementation("io.github.ilyankin:problem-details-xml")
        implementation("io.github.ilyankin:problem-details-ktor")
        implementation("io.github.ilyankin:problem-details-ktor-client")
    }
}
