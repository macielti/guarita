# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

- `lein test` — run all unit/integration tests (CI uses this).
- `lein test :only guarita.foo-test/bar` — run a single test var.
- `lein lint` — clojure-lsp `clean-ns` + `format` + `diagnostics` (surfaces clj-kondo findings). `lein lint-fix` auto-applies `clean-ns` + `format`.
- `lein run` — start the HTTP service on port 8000. REPL equivalent: `(guarita.components/start-system!)` (returns the Integrant system map; hold it to `ig/halt!` later).
- `lein uberjar` then `lein native` — build the GraalVM native image. Requires `native-image` on `PATH`; the `Dockerfile` chains both inside a GraalVM 23 image end-to-end.

## Architecture

- Integrant system is assembled in `src/guarita/components.clj` under the `arranjo` map. Three components come from the private `common-clj` / `service` libs: config → routes → service. Adding a component means adding a key to `arranjo` and (usually) threading it into the `:components` map passed to `::component.service/service` so handlers can destructure it from interceptor context.
- Pedestal handlers live under `src/guarita/diplomat/http_server/`. Each handler receives the Integrant components map at `(:components ctx)` — see `hello_world.clj` (`{{:keys [datalevin config]} :components}`). Register the handler in the route vector at `src/guarita/diplomat/http_server.clj`.
- "Diplomat" namespaces are the Nubank convention for boundary adapters (HTTP in, HTTP-out clients, etc.). Keep core logic out of them.
- Config is environment-keyed in `resources/config.edn`; the running env (`:prod`) is selected in `components.clj`. Service binds `0.0.0.0:8000`.

## Conventions / gotchas

- `hashp` is auto-injected in the `:dev` profile — `#p form` prints `form` and its value at the REPL. Do not commit `#p` calls.
- Test paths `test/unit`, `test/integration`, `test/helpers` are declared in `project.clj` but the directories don't exist yet; create them when adding the first tests.
- `lein native` is sensitive to reflection — `reflect-config.json` is the place to add reflective classes; `:initialize-at-build-time` is enabled globally via `graal-build-time`.
- `.clj-kondo/config.edn` lint-as's `pg.pool/with-connection` as `let`, hinting that PostgreSQL via `pg.pool` is expected (no DB code in-tree yet).
- The user's global Clojure style guide already governs code style; do not restate it here.
