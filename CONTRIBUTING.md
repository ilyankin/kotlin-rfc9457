# Contributing

Issues and pull requests are welcome. Bug reports, feature proposals, and questions help prioritize development.

## Where things go

| Issue type | Destination |
|---|---|
| Bug report | [Issues](https://github.com/ilyankin/kotlin-rfc9457/issues) (include version, artifact, and reproducer) |
| Feature proposal | [Issues](https://github.com/ilyankin/kotlin-rfc9457/issues) (check existing issues before opening a new one) |
| Question | [Discussions](https://github.com/ilyankin/kotlin-rfc9457/discussions) |
| Security vulnerability | Follow [`SECURITY.md`](SECURITY.md) and report privately, not in a public issue |

When asking a specification question, cite the relevant section of [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457).

## Building

```bash
./gradlew build          # Compile, run tests, and check ABI (everything CI gates on except docs)
```

The Gradle wrapper automatically provisions both daemon JVM 21 and compilation toolchain JVM 17.

Kotlin Multiplatform builds do not register a root `test` task. Run tests through target-specific tasks:

```bash
./gradlew :problem-details-core:jvmTest
./gradlew :problem-details-core:macosArm64Test
./gradlew :problem-details-core:jvmTest --tests "io.github.ilyankin.rfc9457.ProblemBuilderTest"
```

Other test tasks include `jsNodeTest`, `wasmJsNodeTest`, `linuxX64Test`, `mingwX64Test`, and `iosSimulatorArm64Test`. Native test tasks run only on supported host systems (macOS for Apple targets, Windows for `mingwX64`, Linux for `linuxX64`). `./gradlew build` runs available local targets and skips unsupported native targets with a warning; CI runs tests on dedicated runners for each platform.

Tests use [Kotest](https://kotest.io) on JUnit Platform for the JVM. For non-JVM targets, the `io.kotest` Gradle plugin and KSP generate test registration code. Nearly all tests live in `commonTest`. Logging tests are located in `problem-details-ktor/src/jvmTest` because Ktor's `Logger` is an `expect interface` typealiased to `org.slf4j.Logger` on the JVM, which prevents writing a recording logger in common code.

## CI requirements

- **ABI validation.** Each module dumps its public API surface to `api/*.api` (JVM) and `api/*.klib.api` (klib targets). Any change to a public signature fails the build until you run `./gradlew updateKotlinAbi` and commit the updated dumps. Review both diffs before committing to ensure no unintended API exposure.
- **KDoc requirements.** Dokka builds run with `reportUndocumented` and `failOnWarning` enabled. In CI, `dokkaGenerate` checks that all public declarations are documented and that KDoc links resolve. This check runs in CI rather than in local `check` because Dokka requires network access.
- **Sample code verification.** Public entry point `@sample` blocks reference functions in `src/commonTest/kotlin/io/github/ilyankin/rfc9457/samples`. Samples compile and run as tests, so signature changes require updating the corresponding sample.
- **Isolated Projects compatibility.** CI builds with `-Dorg.gradle.isolated-projects=true`. Build logic must reside in convention plugins under `build-logic/`. Do not use `subprojects { }` or `allprojects { }` blocks in build scripts.

## Code conventions

- **Explicit API mode.** Kotlin `explicitApi()` is enabled. Every public declaration requires an explicit visibility modifier and return type.
- **Flat extension members.** RFC 9457 §3.2 extension members must serialize at the top level of the document, never nested under an `extensions` key. Both JSON and XML codecs enforce this structure.
- **Strict and lenient accessor pairs.** When adding accessors for `ProblemValue` or extensions, implement both variants: a strict accessor that throws on type mismatches (e.g. `.string`) and an `OrNull` twin that returns `null` (e.g. `.stringOrNull`), per RFC 9457 §3 consumer requirements.
- **Multiplatform compatibility.** All library code resides in `commonMain` without `expect`/`actual` declarations. All modules publish for every declared target. Avoid JVM-only constructs in common code (such as Java `AutoCloseable.use` or JVM SLF4J formatting). Open an issue before proposing JVM-specific public declarations.
- **Dependency scopes (`api` vs. `implementation`).** Gradle `api` dependencies map to Maven `compile` scope, while `implementation` maps to `runtime`. Any type that appears in a public signature, including thrown exception types, must be declared as `api` to avoid compile-time failures for Maven consumers.
- **Module boundaries and XML isolation.** `problem-details-ktor` must never depend on XML modules. Applications using only JSON should not pull in an XML parser. Optional features are partitioned into separate artifacts (such as `problem-details-ktor-xml` and `problem-details-ktor-openapi-xml`). Missing dependencies must fail at compile time at the call site rather than throwing `NoClassDefFoundError` at runtime.

## Pull requests

Keep pull requests focused on a single change. Use one logical change per commit with a one-line imperative subject (e.g. `fix(core): Reject negative status`). Commits in this repository do not use commit message bodies.

Bug fixes must include a reproducing test. Any consumer-visible change must include a `CHANGELOG.md` entry under `## [Unreleased]`.

Breaking changes are permitted during `0.x`, but mark them explicitly in the PR description so they are documented under Breaking Changes in the changelog.

## License

By contributing, you agree that your work is licensed under the [Apache License 2.0](LICENSE). There is no CLA.
