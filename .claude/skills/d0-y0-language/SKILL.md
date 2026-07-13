---
name: d0-y0-language
description: Working on the D0 language and its y0 semantics in this repo (d0/d0.y0, d0/doc/*-spec.md). Use when editing y0 rules, adding language features, or extending the executable Markdown spec with positive/negative examples. Covers the spec-as-tests workflow, y0 rule idioms, and the rule-ordering/dispatch and error-message-matching gotchas.
---

# D0 / y0 language work

`D0` is a language whose semantics are written in `$y_0$` (a logic language).
The semantics live in `d0/d0.y0`; the language is specified and tested by
executable Markdown specs in `d0/doc/*-spec.md`.

## Test workflow

- The shell resets to the repo root (`/home/boaz/clj/y0`) between calls.
  **Always `cd /home/boaz/clj/y0/d0` first**, or you'll pick up the root
  `lang-conf.edn` (y1/c0) and hit `Could not find path for module "c0"`.
- Run the spec directly (fast iteration):
  ```
  cd /home/boaz/clj/y0/d0 && lein run -m y0.main -p "$(realpath .)" -c lang-conf.edn -s doc/*-spec.md
  ```
  Passing output ends with `N Succeeded`. `WARNING:` lines about `y0.core`
  re-referring `clojure.core` are noise — filter with `grep -v "^WARNING"`.
- Full suite (spec + midje): `bash d0/test.sh`.
- New surface keywords (e.g. `deftype`) must be added to `:root-symbols` in
  `d0/lang-conf.edn` so unqualified source symbols resolve to `d0.core/<sym>`.

## Spec (`*-spec.md`) format — these ARE the tests

Each example is a ` ```clojure ` block immediately followed by a ` ```status `
block. The status is either `Success` or `ERROR: <message>`. The runner
compares the reported error against `<message>` **exactly**, so:

- Each error gets an auto-appended context suffix: ` in <form>`, where `<form>`
  is the enclosing top-level form being checked, abbreviated with `...`
  (e.g. ` in (impl [] (my-trait) ...)`). It's part of the reported message, so
  `<message>` must include it — copy it from the actual output (see the loop
  below) rather than guessing.
- y0 **abbreviates nested forms**: `(deftype S Int64)` renders as
  `(deftype ...)`; a list of them as `((deftype ...) (deftype ...))`.
- Bare symbols render **unqualified** (`T`, `not-a-type`); reified
  data/maps render **qualified** (`example/my-trait`, `{:trait example/... }`).
- Reliable loop: write the example with a best-guess status, run, read the
  actual message from the failure (`The wrong error was reported: <actual>`),
  paste it back into the `status` block, re-run.

## y0 rule idioms (as used in d0.y0)

- `(all [vars] (pred args) <- goal ...)` — a rule. `(all [vars] (pred args ! "msg" x ...))`
  is the base/error case (message args joined by spaces). `(fact (pred ...))`
  asserts a ground fact. `(all [vars] (head) => stmt ...)` is a translation
  rule that emits facts/definitions when `head` is encountered in source.
- `(given <stmt-block> goal ...)` proves the goals with the stmt-block's facts
  added to scope; `(assert goal)` checks a goal and surfaces its explanation;
  `(exist [v ...] goal ...)` introduces fresh logic vars. Bindings from goals
  inside `assert`/`given` propagate to the enclosing rule body.
- **Store-then-query pattern** (retrieve data recorded elsewhere): at
  definition time emit `(fact (pred key value))`; provide a base
  `(all [k v] (pred k v ! "internal error ..."))` so the predicate is defined;
  query with the key bound. See `trait-decls-list` (recorded in `deftrait`,
  queried in `impl`).

## Rule ordering & dispatch — the main gotchas

These caused real errors in this repo; internalize them:

1. **Dispatch on the FIRST argument.** y0 indexes rules by arg 1. A rule that
   leaves arg 1 a variable while refining a later arg conflicts:
   `Error: The rule for (p _ (foo ...)) conflicts with a previous rule
   defining (p _ _)`. Fix: make the thing you dispatch on the first argument.
   (When a predicate must validate X against Y, put **X first** and dispatch on
   X's structure.)
2. **Generic before specific.** y0 errors `Rule X must be defined before rule Y
   because it is more generic`. Order rules from most- to least-generic. Note a
   non-empty list pattern `(x & xs)` counts as **more generic** than the
   empty-list literal `()`, so `(x & xs)` rules come *before* the `()` fact
   (see `form-tail`, and `defs-match-decls`).
3. **Need to branch on a second arg once the first is fixed?** You can't (arg-1
   dispatch only). Delegate to a **helper predicate** that takes that value as
   *its* first argument. E.g. once `defs` is `()`, `defs-match-decls` calls
   `all-decls-defined` to dispatch on the remaining `decls`.

## Editing .y0 files

- clj-kondo diagnostics ("Unresolved symbol: `all`/`fact`/`exist`/`!`/…",
  "Unresolved namespace d0.core") are **false positives** — `.y0` is not
  Clojure. Ignore them.
- Section comments use `;; ##`, `;; ###`. Namespaced core keywords are written
  `d0.core/decltype` etc. in rule heads/patterns.
