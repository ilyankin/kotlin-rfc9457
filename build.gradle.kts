import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootExtension

// Module configuration lives in `build-logic`. The root project aggregates what needs every
// module's output in one place: documentation, and now coverage.
plugins {
    id("rfc9457.docs-aggregation")
    alias(libs.plugins.kover)
}

// Floors for known-vulnerable transitives of the Karma/Mocha test toolchain the `js`/`wasmJs`
// targets generate into `kotlin-js-store/`. None of these ship — they only run `jsNodeTest`/
// `wasmJsNodeTest` — but Dependabot alerts on them anyway (dependency-submission#193), and a
// normal bump is blocked because mocha/karma still declare the older majors. After changing a
// floor here, regenerate the matching lockfile: `kotlinUpgradeYarnLock` for `js`,
// `kotlinWasmUpgradeYarnLock` for `wasm` (they resolve independently since Kotlin 2.3.21 split
// the Yarn plugin per web target).
plugins.withType<YarnPlugin> {
    the<YarnRootExtension>().apply {
        resolution("ws", "8.21.0") // GHSA-96hv-2xvq-fx4p
        resolution("serialize-javascript", "7.0.5") // GHSA-qj8w-gfj5-8c6v, GHSA-5c6j-r48x-rmvq
        resolution("diff", "8.0.3") // GHSA-73rr-hh4g-fpgx
    }
}

plugins.withType<WasmYarnPlugin> {
    the<WasmYarnRootExtension>().apply {
        resolution("ws", "8.21.0") // GHSA-96hv-2xvq-fx4p
    }
}

dependencies {
    dokka(project(":problem-details-core"))
    dokka(project(":problem-details-ktor"))
    dokka(project(":problem-details-xml"))
    dokka(project(":problem-details-ktor-xml"))
    dokka(project(":problem-details-ktor-client"))
    dokka(project(":problem-details-ktor-client-xml"))
    dokka(project(":problem-details-ktor-validation"))
    dokka(project(":problem-details-ktor-openapi"))
    dokka(project(":problem-details-ktor-openapi-xml"))

    // Kover is also applied per module (`rfc9457.kmp-library`, so each module's own tests get
    // instrumented); this declares the root as the merging module that combines them all into one
    // report. `:koverXmlReport` here is what a Codecov upload step will consume.
    kover(project(":problem-details-core"))
    kover(project(":problem-details-ktor"))
    kover(project(":problem-details-xml"))
    kover(project(":problem-details-ktor-xml"))
    kover(project(":problem-details-ktor-client"))
    kover(project(":problem-details-ktor-client-xml"))
    kover(project(":problem-details-ktor-validation"))
    kover(project(":problem-details-ktor-openapi"))
    kover(project(":problem-details-ktor-openapi-xml"))
}
