# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

- `lein lint-fix` — auto-applies `clean-ns` + `format`. Run this first before `lein lint`.
- `lein lint` — clojure-lsp `clean-ns` + `format` + `diagnostics` (surfaces clj-kondo findings).
- `lein test` — run all unit/integration tests (CI uses this).
- `lein test :only guarita.foo-test/bar` — run a single test var.
- `lein run` — start the HTTP service on port 8000. REPL equivalent: `(guarita.components/start-system!)` (returns the Integrant system map; hold it to `ig/halt!` later).
- `lein uberjar` then `lein native` — build the GraalVM native image. Requires `native-image` on `PATH`; the `Dockerfile` chains both inside a GraalVM 23 image end-to-end.

## Architecture

Hexagonal architecture with the following layers (each entity gets its own file per layer):

- **`wire/in/<entity>.clj`** — Prismatic/schema for incoming HTTP body shapes. One schema per entity file. Closely related entities (e.g. `Transaction` + `LastTransaction`) share a file. Used by `service.interceptors/wire-in-body-schema` at the route level — not validated inside handlers.
- **`wire/out/<entity>.clj`** — Prismatic/schema for HTTP response body shapes.
- **`wire/postgresql/<entity>.clj`** — Prismatic/schema for DB row shapes (OffsetDateTime, enum strings as returned by PostgreSQL).
- **`models/<entity>.clj`** — Canonical internal domain schemas. Uses `java.time.Instant` for timestamps, keyword enums, boolean predicates (e.g. `online?`, `card-present?`). One entity per file; closely related entities share a file.
- **`adapters/<entity>.clj`** — Conversion functions between layers. Naming: `wire->X`, `postgresql->X`, `X->wire`. Always use destructuring in fn args. Closely related adapters (e.g. `wire->transaction` and `wire->last-transaction`) share a file. The top-level domain adapter (e.g. `adapters/fraud_score.clj`) composes entity adapters.
- **`controllers/<domain>.clj`** — Orchestration: calls db + logic, no HTTP concerns.
- **`logic/<domain>.clj`** — Pure business logic (no I/O, no side effects).
- **`diplomat/http_server/<domain>.clj`** — Pedestal handlers (`s/defn`). Destructure `:json-params` and `:components` from the context map. Call adapters here; delegate to controllers for orchestration.

### Integrant system

Assembled in `src/guarita/components.clj` under the `arranjo` map. Components: config → routes → service. Adding a component means adding a key to `arranjo` and threading it into the `:components` map passed to `::component.service/service`.

**Component definitions**: Local Integrant components (not from libraries) live as separate top-level files at `src/guarita/<component>.clj` (e.g., `dataset.clj`, not `components/dataset.clj`). Import them unaliased to activate their `defmethod` registrations; wire refs to `arranjo`.

### Route registration

Routes live in `src/guarita/diplomat/http_server.clj`. Import and alias `io.pedestal.service.interceptors`, then each route uses:
1. `pedestal.service.interceptors/json-body` — parses JSON body.
2. `service.interceptors/wire-in-body-schema` — validates body against `wire.in.*` schema.
3. Handler fn.

Example:
```clojure
(:require [io.pedestal.service.interceptors :as pedestal.service.interceptors]
          [service.interceptors])

["/endpoint"
 :post [pedestal.service.interceptors/json-body
        (service.interceptors/wire-in-body-schema {:key schema/Schema})
        handler/fn!]
 :route-name :endpoint]
```

### Config

Environment-keyed in `resources/config.edn`; running env (`:prod`) is selected in `components.clj`. Service binds `0.0.0.0:8000`.

### Reference data

`resources/vectors.bin` and `resources/labels.bin` are the k-NN reference dataset used by the fraud-scoring controller. They are **generated at Docker build time** and are not checked into git.

- **Source**: [`references.json.gz`](https://github.com/zanfranceschi/rinha-de-backend-2026/blob/main/resources/references.json.gz) from the rinha-de-backend-2026 repo.
- **`vectors.bin`** — N × 14 matrix of `float32` values in little-endian byte order. Each row is one reference transaction's `FraudScoreVector` (the same 14 features produced by `logic.fraud-score/vectorized`).
- **`labels.bin`** — N bytes of `uint8`. One byte per row: `0` = legit, `1` = fraud.

To regenerate locally (needed for `lein run` / `lein test`):
```
python3 scripts/generate_dataset.py
```

Reading the files at runtime: load `vectors.bin` as a flat `float32` array and reshape to `(n-rows, 14)`; read `labels.bin` byte-by-byte. Row `i` in `vectors.bin` corresponds to byte `i` in `labels.bin`.

KNN search is implemented in `guarita.dataset` as `knn` (sequential) and `knn-parallel` (splits across all CPU cores via `pmap`). `knn-parallel` is the faster implementation and should be used in the controller.

## Conventions / gotchas

- `hashp` is auto-injected in the `:dev` profile — `#p form` prints `form` and its value at the REPL. Do not commit `#p` calls.
- Test paths `test/unit`, `test/integration`, `test/helpers` are declared in `project.clj` but the directories don't exist yet; create them when adding the first tests.
- `lein native` is sensitive to reflection — `reflect-config.json` is the place to add reflective classes; `:initialize-at-build-time` is enabled globally via `graal-build-time`.
- `.clj-kondo/config.edn` lint-as's `pg.pool/with-connection` as `let`, hinting that PostgreSQL via `pg.pool` is expected (no DB code in-tree yet).
- The user's global Clojure style guide already governs code style; do not restate it here.

### Testing

- In unit tests prefer a single `match?` map assertion over per-key `is` + `=` checks.
- Extract expected values to namespace-level `def`s; pass them as overrides to `helpers.schema/generate` and reference the symbols directly in the `match?` pattern — avoid `(:key fixture)` extraction inside `match?`.
- Pass predicate functions (e.g. `inst?`, `number?`, `boolean?`) directly as matcher values — no `m/pred` wrapper needed.
- Require pattern: `[matcher-combinators.test :refer [match?]]`.
- Controllers do not need unit tests; they are thin orchestration layers tested via integration tests (HTTP handler → controller → logic).

### Code style preferences

- For chained adapter → controller calls, use thread-first (`->`): `(-> (adapters.X/wire->X wire) controllers.X/X!)`. The first form explicitly calls the adapter; subsequent forms are threaded.
