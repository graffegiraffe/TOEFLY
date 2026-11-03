## Goal

<!-- One or two sentences: what this phase or change delivers. -->

## Stories

<!-- US-NN.MM covered by this PR, or "none". -->

## Documents and ADRs

<!-- Updated specification documents and ADRs. -->

## Demo

<!-- Steps and the expected result of each step. -->

## Screenshots

<!-- Light and dark themes for every new screen. -->

## Definition of Done

- [ ] Every story criterion has an automated test with the story id
- [ ] Coverage of `domain` and `application` ≥ 80%, ArchUnit rules green
- [ ] Testcontainers component tests and contract tests pass
- [ ] OpenAPI and Avro match `docs/05-api-contracts.md`; schema compatibility check green
- [ ] Flyway migrations apply to an empty database and on top of the previous version
- [ ] Traces, metrics and logs visible in local LGTM
- [ ] Web: Vitest, Playwright, axe; light and dark themes
- [ ] Documents updated docs-first; status table updated
- [ ] Demo run manually and described above
- [ ] CI green
