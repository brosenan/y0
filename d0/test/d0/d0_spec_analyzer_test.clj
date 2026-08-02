(ns d0.d0-spec-analyzer-test
  (:require [midje.sweet :refer [fact => provided anything contains throws]]
            [d0.d0-spec-analyzer :refer [process-d0-spec
                                         analyze-d0 analyze-c0 parse-clojure
                                         wrap-callback]]
            [y0.explanation :refer [explanation-to-str]]))

;; # The d0 Spec Analyzer
;;
;; The d0 spec analyzer processes a d0 spec Markdown file (e.g.,
;; `foo-d0-spec.md`), recognizing _translation examples_ within it.
;;
;; The d0 definitions needed by the examples are given in `wisp` code blocks. A
;; `wisp` block may appear anywhere in the spec, and updates the _current d0
;; prerequisite_. The `wisp` language is used (rather than `clojure`) to
;; distinguish these blocks from the (real) Clojure blocks that hold translation
;; results.
;;
;; A translation example itself consists of two _strictly consecutive_ code
;; blocks (with no lines in between):
;;
;; 1. A `go` block, containing some c0 code, and
;; 2. A `clojure` block, containing the resulting Clojure code.
;;
;; For example:
;; ````md
;; ```wisp
;; <some d0 code>
;; ```
;; Some explanation...
;; ```go
;; <some c0 code>
;; ```
;; ```clojure
;; <the resulting Clojure code>
;; ```
;; ````
;;
;; When the analyzer recognizes an example, it invokes a callback function, given
;; as an argument, with three strings: the most recent d0 code, the c0 code and
;; the resulting Clojure code.

;; ## Recognizing Translation Examples
;;
;; The analyzer scans the spec line by line, tracking the current d0
;; prerequisite and looking for examples.

;; ### A Single Example
;;
;; `process-d0-spec` takes a `callback` and a sequence of `lines`. A `wisp` block
;; sets the d0 prerequisite; a subsequent `go` block followed by a `clojure`
;; block forms an example, for which `callback` is called with the d0 code, the
;; c0 code and the resulting Clojure code.
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

;; The code blocks may span multiple lines. In this case, the lines are joined
;; with newlines.
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

;; ### The d0 Prerequisite
;;
;; The `wisp` block is not part of the example. It may appear anywhere before the
;; example, separated from it by text, and updates the d0 prerequisite.
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

;; When more than one `wisp` block appears, the most recent one takes effect.
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

;; If an example (a `go` block followed by a `clojure` block) appears with no
;; preceding `wisp` block, an exception is thrown.
(fact
 (process-d0-spec callback
                  ["```go"
                   "the c0 code"
                   "```"
                   "```clojure"
                   "(the resulting clojure)"
                   "```"]) =>
 (throws "A translation example must be preceded by a d0 (wisp) block, but none was found"))

;; ### Strict Adjacency
;;
;; The `go` and `clojure` blocks of an example must be _strictly consecutive_:
;; the `clojure` fence must appear on the line immediately following the fence
;; closing the `go` block. Any line in between (text or blank) abandons the
;; example, and the callback is not invoked.
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

;; ### Non-Matching Sequences
;;
;; A `go` block that is not immediately followed by a `clojure` block does not
;; form an example.
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

;; A `clojure` block that is not immediately preceded by a `go` block is ignored.
(fact
 (process-d0-spec callback
                  ["```clojure"
                   "(some clojure)"
                   "```"]) => anything
 (provided
  (callback anything anything anything) => nil :times 0))

;; A `go` block immediately followed by another `go` block does not form an
;; example, but the second block begins a new potential example.
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

;; ### Multiple Examples
;;
;; A single spec may contain any number of examples. The callback is invoked once
;; for each. Examples that follow a single `wisp` block all share it as their d0
;; prerequisite.
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

;; ## Parsing the Code Blocks
;;
;; Once a translation example has been recognized, each of its three code blocks
;; is parsed. The two source-language blocks (d0 and c0) are analyzed using the
;; $y_0$ mechanism into a _predicate store_ (`ps`), while the resulting Clojure
;; block is parsed into a list of s-expressions.
;;
;; Each of the three functions takes a string and returns a
;; [status](status.md).

;; ### d0 Code
;;
;; `analyze-d0` analyzes d0 code (using the `d0` language definition) into a
;; predicate store.
(fact
 (analyze-d0 "(ns example)\n(deftrait simple-trait [])") =>
 (contains {:ok anything}))

;; If the d0 code is invalid, an `:err` status is returned, with the explanation
;; produced by the d0 language definition.
(fact
 (-> (analyze-d0 "(ns example)\n(deftrait \"bad-name\" [])")
     :err
     explanation-to-str) =>
 "A trait name must be a symbol, but \"bad-name\" is given in (deftrait \"bad-name\" [])")

;; ### c0 Code
;;
;; `analyze-c0` analyzes c0 code (using the `c0` language definition) into a
;; predicate store.
(fact
 (analyze-c0 "int32 main() { return 0; }") =>
 (contains {:ok anything}))

;; Invalid c0 code results in an `:err` status.
(fact
 (analyze-c0 "this is not valid c0") =>
 (contains {:err anything}))

;; ### Clojure Code
;;
;; `parse-clojure` parses the resulting Clojure code into a list of
;; s-expressions.
(fact
 (parse-clojure "(foo 1 2) (bar 3)") =>
 {:ok ['(foo 1 2) '(bar 3)]})

;; If the Clojure code cannot be parsed, an `:err` status is returned.
(fact
 (parse-clojure "(foo 1 2") =>
 (contains {:err anything}))

;; ### Assembling the Callback
;;
;; `wrap-callback` bridges between the two levels of callback. It takes a
;; callback that operates on the _parsed_ representations of an example -- the d0
;; predicate store, the c0 predicate store and the list of Clojure s-expressions
;; -- and returns a callback for the spec processor, which operates on the raw
;; code _strings_.
;;
;; The returned callback analyzes each of the three code blocks and unwraps the
;; resulting status before calling the underlying callback.
(defn parsed-callback [d0-ps c0-ps sexprs])
(fact
 ((wrap-callback parsed-callback) "the d0 code" "the c0 code" "the clj code") =>
 anything
 (provided
  (analyze-d0 "the d0 code") => {:ok :the-d0-ps}
  (analyze-c0 "the c0 code") => {:ok :the-c0-ps}
  (parse-clojure "the clj code") => {:ok :the-sexprs}
  (parsed-callback :the-d0-ps :the-c0-ps :the-sexprs) => nil))
