// No `subprojects { }` / `allprojects { }` here — that pattern caused 40 Isolated Projects
// violations (4 unique: Project.apply/extensions/providers/tasks) before the migration. Shared
// config now lives in `build-logic/src/main/kotlin` as two convention plugins, applied per module:
//
//   rfc9457.kmp-library — Kotlin Multiplatform, explicitApi, JVM toolchain, ABI validation, kotest
//   rfc9457.published   — maven-publish, signing, POM metadata; only on modules that ship
//
// Adding a `subprojects { }` block back would reintroduce all 40 violations.
