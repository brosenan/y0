* [The d0 Spec Analyzer](#the-d0-spec-analyzer)
  * [Recognizing Translation Examples](#recognizing-translation-examples)
    * [A Single Example](#a-single-example)
    * [Strict Adjacency](#strict-adjacency)
    * [Non-Matching-Sequences](#non-matching-sequences)
    * [Multiple Examples](#multiple-examples)
  * [Parsing the Code Blocks](#parsing-the-code-blocks)
    * [d0 Code](#d0-code)
    * [c0 Code](#c0-code)
    * [Clojure Code](#clojure-code)
    * [Assembling the Callback](#assembling-the-callback)
```clojure
(ns d0.d0-spec-analyzer-test
  (:require [midje.sweet :refer [fact => provided anything contains]]
            [d0.d0-spec-analyzer :refer [process-d0-spec
                                         analyze-d0 analyze-c0 parse-clojure
                                         wrap-callback]]
            [y0.explanation :refer [explanation-to-str]]))

```
# The d0 Spec Analyzer
The d0 spec analyzer processes a d0 spec Markdown file (e.g.,
`foo-d0-spec.md`), recognizing _translation examples_ within it.
A translation example consists of three _strictly consecutive_ code blocks
(with no lines in between):
1. A `clojure` block, containing some d0 code,
2. A `go` block, containing the equivalent c0 code, and
3. A `clojure` block, containing the resulting Clojure code.
For example:
````md
```clojure
<some d0 code>
```
```go
<some c0 code>
```
```clojure
<the resulting Clojure code>
```
````
When the analyzer recognizes such a pattern, it invokes a callback function,
given as an argument, with the three code snippets, as strings.

## Recognizing Translation Examples
The analyzer scans the spec line by line, looking for the three-block
pattern described above.

### A Single Example
`process-d0-spec` takes a `callback` and a sequence of `lines`. When it
recognizes the three-block pattern it calls `callback` with the d0 code, the
c0 code and the resulting Clojure code.
```clojure
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

```
The code blocks may span multiple lines. In this case, the lines are joined
with newlines.
```clojure
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

```
### Strict Adjacency
The three code blocks must be _strictly consecutive_: the fence opening each
block must appear on the line immediately following the fence closing the
previous block. Any line in between (text or blank) abandons the pattern, and
the callback is not invoked.
```clojure
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

```
### Non-Matching Sequences
A sequence of code blocks with the wrong languages is not recognized, and
the callback is not invoked.
```clojure
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

```
A code block of an unrelated language breaks the sequence: the three blocks
must be _consecutive_.
```clojure
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

```
A `clojure` block immediately followed by another `clojure` block does not
match, but the second block is treated as a potential start of a new example.
Here the second `clojure` block begins a valid example.
```clojure
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

```
### Multiple Examples
A single spec may contain any number of translation examples. The callback is
invoked once for each.
```clojure
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
predicate store.
```clojure
(fact
 (analyze-c0 "int32 main() { return 0; }") =>
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
callback that operates on the _parsed_ representations of an example -- the d0
predicate store, the c0 predicate store and the list of Clojure s-expressions
-- and returns a callback for the spec processor, which operates on the raw
code _strings_.
The returned callback analyzes each of the three code blocks and unwraps the
resulting status before calling the underlying callback.
```clojure
(defn parsed-callback [d0-ps c0-ps sexprs])
(fact
 ((wrap-callback parsed-callback) "the d0 code" "the c0 code" "the clj code") =>
 anything
 (provided
  (analyze-d0 "the d0 code") => {:ok :the-d0-ps}
  (analyze-c0 "the c0 code") => {:ok :the-c0-ps}
  (parse-clojure "the clj code") => {:ok :the-sexprs}
  (parsed-callback :the-d0-ps :the-c0-ps :the-sexprs) => nil))
```

