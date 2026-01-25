# Development Guide

## Prerequisites

- Node.js and npm
- Java (JDK 11+)
- Leiningen (for releases)

## Setup

```bash
npm install
```

## Running Tests

### Unit Tests (Watch Mode)

```bash
npm test
```

Navigate to http://localhost:8021 to view test results. Tests will re-run automatically when source files change.

### Unit Tests (Single Run)

```bash
npm run test:ci
```

Test results are output to the `reports/` directory.

### Render Tests

Visual regression tests that compare rendered output against reference images:

```bash
npm run test:render
```

If tests fail, a diff image is generated at `tests/render_test/diff.png`.

## Running Examples

```bash
npm run examples
```

Navigate to http://localhost:8080 to view the examples.

## Development Workflow

### REPL-driven Development

shadow-cljs provides hot-reloading. Start a watch build and connect your editor's REPL.

### Adding New Entity Types

1. Implement `IEntityType` protocol (and optionally `IUpdateableEntityType` for in-place updates) in `src/main/threeagent/impl/entities.cljs`
2. Add to the `builtin-entity-types` map
3. Or register custom types via the `:entity-types` option in `threeagent.core/render`

### Adding New Tests

- Unit tests go in `src/test/threeagent/`
- Test namespaces must end with `-test` to be picked up by the test runner
- E2E tests are in `src/test/threeagent/e2e/`
- Virtual scene tests are in `src/test/threeagent/virtual_scene/`

## Releasing

Releases are managed through CircleCI with GPG-signed artifacts deployed to Clojars.

### Release Process

1. Ensure all tests pass on `main` branch
2. Create/checkout the `release` branch from `main`:
   ```bash
   git checkout -b release main
   git push -u origin release
   ```
3. CircleCI will run tests automatically
4. Approve the release in CircleCI (manual approval step)
5. CircleCI executes `lein release :patch` which:
   - Verifies all changes are committed
   - Bumps version from SNAPSHOT to release (e.g., `1.0.2-SNAPSHOT` → `1.0.2`)
   - Commits the version change
   - Creates a signed git tag (e.g., `v1.0.2`)
   - Deploys to Clojars
   - Bumps version to next SNAPSHOT (e.g., `1.0.3-SNAPSHOT`)
   - Commits the version change
   - Merges `release` back to `main`
   - Pushes all changes

### Version Bumping

The release uses `:patch` by default. For other version bumps:
- `:patch` - 1.0.1 → 1.0.2
- `:minor` - 1.0.1 → 1.1.0
- `:major` - 1.0.1 → 2.0.0

### Snapshot Deploys

Snapshot versions are automatically deployed to Clojars on every push to `main` after tests pass.

### Manual Local Deploy (for testing)

```bash
lein deploy
```

Requires `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` environment variables and GPG key configured.
