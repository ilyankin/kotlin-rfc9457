plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.multiplatform")

    group = "io.github.ilyankin"
    version = "0.1.0-SNAPSHOT"

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
        explicitApi()
        jvm()
        jvmToolchain(17)

        sourceSets.getByName("commonTest").dependencies {
            implementation(rootProject.libs.kotest.framework.engine)
            implementation(rootProject.libs.kotest.assertions.core)
        }
        // The JVM target discovers and runs kotest specs through the JUnit Platform.
        sourceSets.getByName("jvmTest").dependencies {
            implementation(rootProject.libs.kotest.runner.junit5)
        }
    }

    tasks.withType<Test>().configureEach { useJUnitPlatform() }
}
