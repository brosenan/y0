* [The d0 Spec Analyzer](#the-d0-spec-analyzer)
  * [Recognizing Translation Examples](#recognizing-translation-examples)
    * [A Single Example](#a-single-example)
    * [The d0 Prerequisite](#the-d0-prerequisite)
    * [Strict Adjacency](#strict-adjacency)
    * [Non-Matching-Sequences](#non-matching-sequences)
    * [Multiple Examples](#multiple-examples)
  * [Tracking Results](#tracking-results)
  * [Parsing the Code Blocks](#parsing-the-code-blocks)
    * [d0 Code](#d0-code)
    * [c0 Code](#c0-code)
    * [Clojure Code](#clojure-code)
    * [Assembling the Callback](#assembling-the-callback)
  * [Testing a Translation Example](#testing-a-translation-example)
```clojure
(ns d0.d0-spec-analyzer-test
  (:require [midje.sweet :refer [fact => provided anything contains throws]]
            [d0.compiler :refer [compile]]
            [d0.d0-spec-analyzer :refer [process-d0-spec
                                         analyze-d0 analyze-c0 parse-clojure
                                         wrap-callback d0-test]]
            [y0.explanation :refer [explanation-to-str]]))

```
# The d0 Spec Analyzer
The d0 spec analyzer processes a d0 spec Markdown file (e.g.,
`foo-d0-spec.md`), recognizing _translation examples_ within it.
The d0 definitions needed by the examples are given in `wisp` code blocks. A
`wisp` block may appear anywhere in the spec, and updates the _current d0
prerequisite_. The `wisp` language is used (rather than `clojure`) to
distinguish these blocks from the (real) Clojure blocks that hold translation
results.
A translation example itself consists of two _strictly consecutive_ code
blocks (with no lines in between):
1. A `go` block, containing some c0 code, and
2. A `clojure` block, containing the resulting Clojure code.
For example:
````md
```wisp
<some d0 code>
```
Some explanation...
```go
<some c0 code>
```
```clojure
<the resulting Clojure code>
```
````
When the analyzer recognizes an example, it invokes a callback function, given
as an argument, with three strings: the most recent d0 code, the c0 code and
the resulting Clojure code.

## Recognizing Translation Examples
The analyzer scans the spec line by line, tracking the current d0
prerequisite and looking for examples.

### A Single Example
`process-d0-spec` takes a `callback` and a sequence of `lines`. A `wisp` block
sets the d0 prerequisite; a subsequent `go` block followed by a `clojure`
block forms an example, for which `callback` is called with the d0 code, the
c0 code and the resulting Clojure code.
```clojure
(defn callback [d0-code c0-code clj-code])
(fact
 (process-d0-spec callback
                  ["```wisp"
                   "(the d0 code)"
                   "```"
                   "```go"
                   "the c0 code"
                   "```"
                   "```clojure"
                   "(the resulting clojure)"
                   "```"]) => anything
 (provided
  (callback "(the d0 code)" "the c0 code" "(the resulting clojure)") => nil))

```
The code blocks may span multiple lines. In this case, the lines are joined
with newlines.
```clojure
(fact
 (process-d0-spec callback
                  ["```wisp"
                   "(d0 line 1)"
                   "(d0 line 2)"
                   "```"
                   "```go"
                   "c0 line 1"
                   "c0 line 2"
                   "```"
                   "```clojure"
                   "(clj line 1)"
                   "(clj line 2)"
                   "```"]) => anything
 (provided
  (callback "(d0 line 1)\n(d0 line 2)"
            "c0 line 1\nc0 line 2"
            "(clj line 1)\n(clj line 2)") => nil))

```
### The d0 Prerequisite
The `wisp` block is not part of the example. It may appear anywhere before the
example, separated from it by text, and updates the d0 prerequisite.
```clojure
(fact
 (process-d0-spec callback
                  ["```wisp"
                   "(the d0 code)"
                   "```"
                   "Some explanatory text."
                   "```go"
                   "the c0 code"
                   "```"
                   "```clojure"
                   "(the resulting clojure)"
                   "```"]) => anything
 (provided
  (callback "(the d0 code)" "the c0 code" "(the resulting clojure)") => nil))

```
When more than one `wisp` block appears, the most recent one takes effect.
```clojure
(fact
 (process-d0-spec callback
                  ["```wisp"
                   "(old d0 code)"
                   "```"
                   "```wisp"
                   "(new d0 code)"
                   "```"
                   "```go"
                   "the c0 code"
                   "```"
                   "```clojure"
                   "(the resulting clojure)"
                   "```"]) => anything
 (provided
  (callback "(new d0 code)" "the c0 code" "(the resulting clojure)") => nil))

```
If an example (a `go` block followed by a `clojure` block) appears with no
preceding `wisp` block, an exception is thrown.
```clojure
(fact
 (process-d0-spec callback
                  ["```go"
                   "the c0 code"
                   "```"
                   "```clojure"
                   "(the resulting clojure)"
                   "```"]) =>
 (throws "A translation example must be preceded by a d0 (wisp) block, but none was found"))

```
### Strict Adjacency
The `go` and `clojure` blocks of an example must be _strictly consecutive_:
the `clojure` fence must appear on the line immediately following the fence
closing the `go` block. Any line in between (text or blank) abandons the
example, and the callback is not invoked.
```clojure
(fact
 (process-d0-spec callback
                  ["```wisp"
                   "(the d0 code)"
                   "```"
                   "```go"
                   "the c0 code"
                   "```"
                   ""
                   "```clojure"
                   "(the resulting clojure)"
                   "```"]) => anything
 (provided
  (callback anything anything anything) => nil :times 0))

```
### Non-Matching Sequences
A `go` block that is not immediately followed by a `clojure` block does not
form an example.
```clojure
(fact
 (process-d0-spec callback
                  ["```wisp"
                   "(the d0 code)"
                   "```"
                   "```go"
                   "the c0 code"
                   "```"
                   "```python"
                   "the python code"
                   "```"]) => anything
 (provided
  (callback anything anything anything) => nil :times 0))

```
A `clojure` block that is not immediately preceded by a `go` block is ignored.
```clojure
(fact
 (process-d0-spec callback
                  ["```clojure"
                   "(some clojure)"
                   "```"]) => anything
 (provided
  (callback anything anything anything) => nil :times 0))

```
A `go` block immediately followed by another `go` block does not form an
example, but the second block begins a new potential example.
```clojure
(fact
 (process-d0-spec callback
                  ["```wisp"
                   "(the d0 code)"
                   "```"
                   "```go"
                   "(not the c0 code)"
                   "```"
                   "```go"
                   "the c0 code"
                   "```"
                   "```clojure"
                   "(the resulting clojure)"
                   "```"]) => anything
 (provided
  (callback "(the d0 code)" "the c0 code" "(the resulting clojure)") => nil))

```
### Multiple Examples
A single spec may contain any number of examples. The callback is invoked once
for each. Examples that follow a single `wisp` block all share it as their d0
prerequisite.
```clojure
(fact
 (process-d0-spec callback
                  ["```wisp"
                   "(the d0 code)"
                   "```"
                   "```go"
                   "c0 one"
                   "```"
                   "```clojure"
                   "(clj one)"
                   "```"
                   "Some text between examples."
                   "```go"
                   "c0 two"
                   "```"
                   "```clojure"
                   "(clj two)"
                   "```"]) => anything
 (provided
  (callback "(the d0 code)" "c0 one" "(clj one)") => nil
  (callback "(the d0 code)" "c0 two" "(clj two)") => nil))

```
## Tracking Results
The `callback` is expected to return a [status](status.md): `{:ok ...}` for a
successful example, or `{:err explanation}` for a failed one.
`process-d0-spec` accumulates these results in the state it returns, under two
keys:
* `:success`, counting the number of successful examples, and
* `:errors`, a list of the explanations returned by failed examples.

A successful example increments the `:success` count.
```clojure
(fact
 (process-d0-spec callback
                  ["```wisp"
                   "(the d0 code)"
                   "```"
                   "```go"
                   "the c0 code"
                   "```"
                   "```clojure"
                   "(the resulting clojure)"
                   "```"]) => (contains {:success 1})
 (provided
  (callback "(the d0 code)" "the c0 code" "(the resulting clojure)") =>
  {:ok nil}))

```
A failed example adds its explanation to `:errors`.
```clojure
(fact
 (process-d0-spec callback
                  ["```wisp"
                   "(the d0 code)"
                   "```"
                   "```go"
                   "the c0 code"
                   "```"
                   "```clojure"
                   "(the resulting clojure)"
                   "```"]) => (contains {:errors [["Some error"]]})
 (provided
  (callback "(the d0 code)" "the c0 code" "(the resulting clojure)") =>
  {:err ["Some error"]}))

```
Over multiple examples, successes and errors are accumulated separately.
```clojure
(fact
 (process-d0-spec callback
                  ["```wisp"
                   "(the d0 code)"
                   "```"
                   "```go"
                   "c0 one"
                   "```"
                   "```clojure"
                   "(clj one)"
                   "```"
                   "```go"
                   "c0 two"
                   "```"
                   "```clojure"
                   "(clj two)"
                   "```"
                   "```go"
                   "c0 three"
                   "```"
                   "```clojure"
                   "(clj three)"
                   "```"]) => (contains {:success 2
                                         :errors [["error two"]]})
 (provided
  (callback "(the d0 code)" "c0 one" "(clj one)") => {:ok nil}
  (callback "(the d0 code)" "c0 two" "(clj two)") => {:err ["error two"]}
  (callback "(the d0 code)" "c0 three" "(clj three)") => {:ok nil}))

```
The code locations within a reported error refer to the code block they came
from (each block is analyzed with its language name as its `:path`). These
locations are converted to point at the correct line in the spec `path`. In
the following, the error refers to `path` `"c0"` at (block-relative) row 1.
The `go` block opens on line 4, so its content begins on line 5, and the
location is converted accordingly.
```clojure
(fact
 (def loc-err-state
   (process-d0-spec callback
                    ["```wisp"                    ;; line 1
                     "(the d0 code)"              ;; line 2
                     "```"                        ;; line 3
                     "```go"                      ;; line 4
                     "the c0 code"                ;; line 5
                     "```"                        ;; line 6
                     "```clojure"                 ;; line 7
                     "(the resulting clojure)"    ;; line 8
                     "```"]                       ;; line 9
                    "path/to/spec.md")) => #'loc-err-state
 (provided
  (callback "(the d0 code)" "the c0 code" "(the resulting clojure)") =>
  {:err [(with-meta `foo {:path "c0"
                          :start 1000003
                          :end 1000005})]})
 (-> loc-err-state :errors first first meta) => {:path "path/to/spec.md"
                                                 :start 5000003
                                                 :end 5000005})

```
## Parsing the Code Blocks
Once a translation example has been recognized, each of its three code blocks
is parsed. The two source-language blocks (d0 and c0) are analyzed using the
$y_0$ mechanism into a _predicate store_ (`ps`), while the resulting Clojure
block is parsed into a list of s-expressions.
Each of the three functions takes a string and returns a
[status](status.md).

### d0 Code
`analyze-d0` analyzes d0 code (using the `d0` language definition) into a
predicate store.
```clojure
(fact
 (analyze-d0 "(ns example)\n(deftrait simple-trait [])") =>
 (contains {:ok anything}))

```
If the d0 code is invalid, an `:err` status is returned, with the explanation
produced by the d0 language definition.
```clojure
(fact
 (-> (analyze-d0 "(ns example)\n(deftrait \"bad-name\" [])")
     :err
     explanation-to-str) =>
 "A trait name must be a symbol, but \"bad-name\" is given in (deftrait \"bad-name\" [])")

```
### c0 Code
`analyze-c0` analyzes c0 code (using the `c0` language definition) into a
pair consisting of a predicate store and a parse tree of the root expression
(call to `test()`).
```clojure
(fact
 (analyze-c0 "int32 test() { return 0; }") =>
 (contains {:ok anything}))

```
Invalid c0 code results in an `:err` status.
```clojure
(fact
 (analyze-c0 "this is not valid c0") =>
 (contains {:err anything}))

```
### Clojure Code
`parse-clojure` parses the resulting Clojure code into a list of
s-expressions.
```clojure
(fact
 (parse-clojure "(foo 1 2) (bar 3)") =>
 {:ok ['(foo 1 2) '(bar 3)]})

```
If the Clojure code cannot be parsed, an `:err` status is returned.
```clojure
(fact
 (parse-clojure "(foo 1 2") =>
 (contains {:err anything}))

```
### Assembling the Callback
`wrap-callback` bridges between the two levels of callback. It takes a
callback that operates on the _parsed_ representations of an example -- the
parse tree of the root expression (the call to `test()`), the c0 predicate
store, the d0 predicate store and the list of Clojure s-expressions -- and
returns a callback for the spec processor, which operates on the raw code
_strings_.
The returned callback analyzes each of the three code blocks and threads
their statuses using `let-s`. On success, it calls the underlying callback
with the four parsed values and returns its status.
```clojure
(defn parsed-callback [root-expr c0-ps d0-ps sexprs])
(fact
 ((wrap-callback parsed-callback) "the d0 code" "the c0 code" "the clj code") =>
 {:ok :the-result}
 (provided
  (analyze-d0 "the d0 code") => {:ok :the-d0-ps}
  (analyze-c0 "the c0 code") => {:ok [:the-c0-ps :the-root-tree]}
  (parse-clojure "the clj code") => {:ok :the-sexprs}
  (parsed-callback :the-root-tree :the-c0-ps :the-d0-ps :the-sexprs) => {:ok :the-result}))

```
If any of the three code blocks fails to parse, its `:err` status is
propagated and the underlying callback is not called.
```clojure
(fact
 ((wrap-callback parsed-callback) "the d0 code" "the c0 code" "the clj code") =>
 {:err ["some c0 error"]}
 (provided
  (analyze-d0 "the d0 code") => {:ok :the-d0-ps}
  (analyze-c0 "the c0 code") => {:err ["some c0 error"]}
  (parse-clojure "the clj code") => anything :times 0
  (parsed-callback anything anything anything) => anything :times 0))

```
## Testing a Translation Example
`d0-test` is the callback, in its "parsed" form, that tests a single
translation example. It compiles the c0 code (given the d0 definitions) using
[[d0.compiler/compile]], and compares the resulting s-expressions against the
expected Clojure code from the example.

If the compiled code is identical to the expected code, the test succeeds.
```clojure
(fact
 (d0-test :root-expr :c0-ps :d0-ps ['(defn main [args] 42)]) => {:ok nil}
 (provided
  (compile :root-expr :c0-ps :d0-ps) => {:ok ['(defn main [args] 42)]}))

```
If they differ, an error is returned. Its explanation contains a textual diff
between the expected and the actual code.
```clojure
(fact
 (def mismatch (d0-test :root-expr :c0-ps :d0-ps ['(defn main [args] 43)])) => #'mismatch
 (provided
  (compile :root-expr :c0-ps :d0-ps) => {:ok ['(defn main [args] 42)]})
 (-> mismatch :err first) => "The compiled code does not match the expected code:"
 (-> mismatch :err second) => string?)

```
If the compilation itself fails, its error is propagated.
```clojure
(fact
 (d0-test :root-expr :c0-ps :d0-ps ['(defn main [args] 42)]) => {:err ["compilation failed"]}
 (provided
  (compile :root-expr :c0-ps :d0-ps) => {:err ["compilation failed"]}))
```

