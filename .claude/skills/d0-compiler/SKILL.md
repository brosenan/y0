---
name: d0-compiler
description: Adding D0 language features to the compiler in this repo (d0/src/d0/compiler.clj, d0/doc/*-d0spec.md). Use when implementing code generation for a new D0 expression/form (translating D0 to Clojure) and extending the executable Markdown compiler spec with examples. Covers the d0spec-as-tests workflow, the compile-form multimethod idiom, and the static-semantics/syntax-quote/block-adjacency gotchas. Complements the d0-y0-language skill (static semantics).
---

# D0 compiler work (code generation)

The D0 compiler translates a D0 program into Clojure code. It lives in
`d0/src/d0/compiler.clj` and is specified/tested by executable Markdown specs in
`d0/doc/*-d0spec.md`. This is the **dynamic-semantics / codegen** side, distinct
from the **static semantics** in `d0/d0.y0` (`*-spec.md`, see the
`d0-y0-language` skill). A feature usually needs both: static rules in `d0.y0`
first, then compilation here.

## Test workflow

- The shell resets to the repo root (`/home/boaz/clj/y0`) between calls.
  **Always `cd /home/boaz/clj/y0/d0` first.**
- Run the compiler spec directly (fast iteration):
  ```
  cd /home/boaz/clj/y0/d0 && lein run -m d0.main doc/expressions-d0spec.md
  ```
  (`doc/*-d0spec.md` for all.) Passing output ends with `N Succeeded`.
  `WARNING:` lines about `y0.core` re-referring `clojure.core` are noise —
  filter with `grep -v "^WARNING"`.
- Full suite: `bash d0/test.sh` runs `d0.main` (d0spec), `lein midje`, then
  `y0.main` (static `*-spec.md`).
- Note the naming: `*-d0spec.md` = compiler/codegen tests (run by `d0.main`);
  `*-spec.md` = static-semantics tests (run by `y0.main`). Different files.

## d0spec (`*-d0spec.md`) format — these ARE the tests

Each example is three fenced blocks, and **the `clojure` block must immediately
follow the `go` block** (the state machine abandons the example if anything —
even a blank line — sits between them). Prose may precede the `go` block:

    ```wisp
    (ns example)

    (root-expr "test" [node]
      <D0 expr under test>)
    ```

    And here is an example:
    ```go
    int64 test() { return 0; }
    ```
    ```clojure
    (clojure.core/defn test []
      <expected generated Clojure>)
    ```

- The `wisp` block holds D0 source: a `root-expr` whose body is the expression
  you're compiling. It's a standalone prerequisite — one `wisp` block can be
  reused by a following `go`/`clojure` pair.
- The `go` (c0) block is a dummy source program; its body is disregarded. Keep
  it as the boilerplate `<type> test() { return <val>; }`.
- The `clojure` block is the **exact** expected output. `d0-test` compares the
  compiled s-exprs to it with `deep-diff` (a mismatch prints a diff) **and**
  then `eval`s the result to confirm it compiles.

## Implementing a form in compiler.clj

Codegen is a `compile-form` multimethod dispatching on the **qualified head
symbol as a string**, e.g. `"d0.core/if"`, `"d0.core/let"`. Each method returns
a `(fn [state] ...)` producing a `y0.status` value (`ok`/`{:err ...}`); compose
sub-results with `let-s`. Recurse into sub-expressions via `compile-expr`:

```clojure
(defmethod compile-form "d0.core/if" [[_if cond then else]]
  (fn [state]
    (let-s [cond' (compile-expr cond state)
            then' (compile-expr then state)
            else' (compile-expr else state)]
           (ok `(if ~cond' ~then' ~else')))))
```

- `compile-expr` handles the base cases: seqs dispatch to `compile-form`;
  symbols in `(:bindings state)` map to their Clojure binding; everything else
  (literals) passes through unchanged.
- `state` is a map (`:tree :src-ps :d0-ps :bindings`). To add lexical bindings
  (like `let`), `(update state :bindings assoc d0-var clj-var)` and thread it
  into the sub-`compile-expr` calls — see `compile-let`.
- The default `compile-form` returns an `"Unsupported form"` error, so an
  unimplemented head fails loudly.

## Gotchas — these bit during real work

1. **Static semantics run first.** `d0-test` calls `analyze-d0` (the `d0.y0`
   static checker) before compiling, so every example must be a **valid D0
   program**. E.g. there are no boolean literals — `expression-kind` in `d0.y0`
   accepts only `:int`/`:float`/`:string`, method calls, `let`, `if`, `with`,
   `call`, … Using `true` as an `if` cond fails with
   `Invalid expression true`; use an int literal (`cond`'s Bool type isn't
   checked statically). If the feature is new, add its static rule to `d0.y0`
   first.
2. **New surface keyword?** Add the unqualified symbol to `:root-symbols` in
   `d0/lang-conf.edn` so it resolves to `d0.core/<sym>` (which is what
   `compile-form` dispatches on). `if`, `let`, etc. are already there.
3. **Syntax-quote qualification.** In the emitted template, `` `let `` →
   `clojure.core/let` (it's a var/macro) but `` `if `` stays `if` (special
   forms aren't qualified). So `let` expects `clojure.core/let` output while
   `if` expects bare `if`. When unsure what a head resolves to, check in a repl:
   `echo '(prn `(if a b c))' | lein repl`.
4. **clj-kondo false positives.** `let-s` bindings (`cond'`, `then'`, …) report
   as "Unresolved symbol" because clj-kondo doesn't know the `let-s` macro.
   Ignore them — the existing `compile-let` has the same pattern.

## Reliable loop

Write the example with best-guess expected Clojure, run `d0.main`. On failure it
prints either `Invalid expression ...` (fix the D0 to satisfy static semantics)
or `The compiled code does not match the expected code:` with a diff (align the
`clojure` block to the compiler's actual output, or fix the compiler). Re-run
until `N Succeeded`, then `bash test.sh` for the full suite.
