  * [Pretty-printing-an-Explanation](#pretty-printing-an-explanation)
    * [Stringifying S-Expressions](#stringifying-s-expressions)
    * [Stringifying Explanations](#stringifying-explanations)
```clojure
(ns y0.explanation-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.explanation :refer [explanation-to-str explanation-expr-to-str]]))

```
"Why not" explanations are an important aspect of $y_0$ and are here we describe the
software support for them.

## Pretty-printing an Explanation

A "why not" explanation is a vector of components, including strings and (other)
s-expressions. We begin by discussing how the s-expressions are being stringified.

### Stringifying S-Expressions

The function `explanation-expr-to-str` takes an s-expression and a "budget" of elements
to be printed, and returns a string representing it.

A symbol is stringified without its namespace.
```clojure
(fact (explanation-expr-to-str 'foo/bar 3) => "bar")

```
Numbers are stringified decimally.
```clojure
(fact
 (explanation-expr-to-str 5 3) => "5"
 (explanation-expr-to-str 3.14 3) => "3.14")

```
A sequence (form) is stringified by adding `()` around its contents and spaces between
its elements.
```clojure
(fact (explanation-expr-to-str '(foo/bar 1 2) 3) => "(bar 1 2)")

```
If the number of elements in the sequence exceeds the budget, the remaining elements are
replaced with `...`.
```clojure
(fact (explanation-expr-to-str '(foo/bar 1 2 3) 3) => "(bar 1 2 ...)")

```
Sub-expressions are taken with budget = 1.
```clojure
(fact (explanation-expr-to-str '(foo/bar (+ 1 2) 3 4) 3) => "(bar (+ ...) 3 ...)")

```
Vectors are supported too.
```clojure
(fact (explanation-expr-to-str '[[x y z] 1 [2 3] 4] 3) => "[[x ...] 1 [2 ...] ...]")

```
### Stringifying Explanations

The `explanation-to-str` function takes an explanation and returns a string representing
it. It takes an explanation term and a predstore and return a string representing the explanation.
The role of the predstore will be made clear later.

Strings are taken verbatim, joined with spaces.
```clojure
(fact
 (explanation-to-str ["foo" "bar"] {}) => "foo bar")

```
Non-strings are treated as s-expressions and are stringified with budget 3.
```clojure
(fact
 (explanation-to-str ["foo" 3 '(x/bar 1 2 3)] {}) => "foo 3 (bar 1 2 ...)")
```

