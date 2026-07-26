(ns d0.d0-spec-analyzer-test
  (:require [midje.sweet :refer [fact => provided anything]]
            [d0.d0-spec-analyzer :refer [process-d0-spec]]))

;; # The d0 Spec Analyzer
;;
;; The d0 spec analyzer processes a d0 spec Markdown file (e.g.,
;; `foo-d0-spec.md`), recognizing _translation examples_ within it.
;;
;; A translation example consists of three _strictly consecutive_ code blocks
;; (with no lines in between):
;;
;; 1. A `clojure` block, containing some d0 code,
;; 2. A `go` block, containing the equivalent c0 code, and
;; 3. A `clojure` block, containing the resulting Clojure code.
;;
;; For example:
;; ````md
;; ```clojure
;; <some d0 code>
;; ```
;; ```go
;; <some c0 code>
;; ```
;; ```clojure
;; <the resulting Clojure code>
;; ```
;; ````
;;
;; When the analyzer recognizes such a pattern, it invokes a callback function,
;; given as an argument, with the three code snippets, as strings.

;; ## Recognizing a Translation Example
;;
;; `process-d0-spec` takes a `callback` and a sequence of `lines`. When it
;; recognizes the three-block pattern it calls `callback` with the d0 code, the
;; c0 code and the resulting Clojure code.
(defn callback [d0-code c0-code clj-code])
(fact
 (process-d0-spec callback
                  ["```clojure"
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
                  ["```clojure"
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

;; ## Strict Adjacency
;;
;; The three code blocks must be _strictly consecutive_: the fence opening each
;; block must appear on the line immediately following the fence closing the
;; previous block. Any line in between (text or blank) abandons the pattern, and
;; the callback is not invoked.
(fact
 (process-d0-spec callback
                  ["```clojure"
                   "(the d0 code)"
                   "```"
                   ""
                   "```go"
                   "the c0 code"
                   "```"
                   "```clojure"
                   "(the resulting clojure)"
                   "```"]) => anything
 (provided
  (callback anything anything anything) => nil :times 0))

;; ## Non-Matching Sequences
;;
;; A sequence of code blocks with the wrong languages is not recognized, and
;; the callback is not invoked.
(fact
 (process-d0-spec callback
                  ["```clojure"
                   "(the d0 code)"
                   "```"
                   "```python"
                   "the python code"
                   "```"
                   "```clojure"
                   "(the resulting clojure)"
                   "```"]) => anything
 (provided
  (callback anything anything anything) => nil :times 0))

;; A code block of an unrelated language breaks the sequence: the three blocks
;; must be _consecutive_.
(fact
 (process-d0-spec callback
                  ["```clojure"
                   "(the d0 code)"
                   "```"
                   "```python"
                   "the python code"
                   "```"
                   "```go"
                   "the c0 code"
                   "```"
                   "```clojure"
                   "(the resulting clojure)"
                   "```"]) => anything
 (provided
  (callback anything anything anything) => nil :times 0))

;; A `clojure` block immediately followed by another `clojure` block does not
;; match, but the second block is treated as a potential start of a new example.
;; Here the second `clojure` block begins a valid example.
(fact
 (process-d0-spec callback
                  ["```clojure"
                   "(not the d0 code)"
                   "```"
                   "```clojure"
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

;; ## Multiple Examples
;;
;; A single spec may contain any number of translation examples. The callback is
;; invoked once for each.
(fact
 (process-d0-spec callback
                  ["```clojure"
                   "(d0 one)"
                   "```"
                   "```go"
                   "c0 one"
                   "```"
                   "```clojure"
                   "(clj one)"
                   "```"
                   "Some text between examples."
                   "```clojure"
                   "(d0 two)"
                   "```"
                   "```go"
                   "c0 two"
                   "```"
                   "```clojure"
                   "(clj two)"
                   "```"]) => anything
 (provided
  (callback "(d0 one)" "c0 one" "(clj one)") => nil
  (callback "(d0 two)" "c0 two" "(clj two)") => nil))
