## What this changes

<!-- A sentence or two. If it closes an issue, write `Fixes #123`. -->

## Checklist

<!-- Skip what does not apply; every line maps to something CI actually checks. -->

- [ ] `./gradlew build` passes — compilation, the Kotest suites, and the `api/*.api` check.
- [ ] Public API changed: ran `./gradlew updateKotlinAbi`, and the resulting `api/*.api` diff is part
      of this PR and exposes only what I intended.
- [ ] New or renamed public declaration: it carries KDoc. CI runs `dokkaGenerate` with
      `failOnWarning`, so an undocumented one fails the build even though `check` stays green.
- [ ] Touched a function referenced by `@sample`: updated the sample under
      `src/commonTest/kotlin/io/github/ilyankin/rfc9457/samples`.
- [ ] Consumer-visible change: added an entry to `CHANGELOG.md` under `## [Unreleased]`.
