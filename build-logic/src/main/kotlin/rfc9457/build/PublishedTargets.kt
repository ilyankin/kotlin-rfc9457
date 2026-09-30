package rfc9457.build

/**
 * The Kotlin targets every library module publishes, by target name. `rfc9457.kmp-library` declares
 * them through the Kotlin DSL and checks that the declared set equals this one; `problem-details-bom`
 * derives one platform artifact per module and target from it. Adding a target means adding it in
 * both places, and the check fails the build until both agree.
 */
object PublishedTargets {
    val names: Set<String> =
        setOf(
            "jvm",
            "js",
            "wasmJs",
            "linuxX64",
            "linuxArm64",
            "mingwX64",
            "macosArm64",
            "iosArm64",
            "iosSimulatorArm64",
        )

    /** KGP lowercases the target name for the artifact suffix, and hyphenates `wasmJs`. */
    fun artifactId(
        module: String,
        target: String,
    ): String = if (target == "wasmJs") "$module-wasm-js" else "$module-${target.lowercase()}"
}
