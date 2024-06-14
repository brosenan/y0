  * [Pretty-printing-an-Explanation](#pretty-printing-an-explanation)
    * [Stringifying S-Expressions](#stringifying-s-expressions)
    * [Stringifying Explanations](#stringifying-explanations)
  * [Code Location](#code-location)
```clojure
(ns y0.explanation-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.explanation :refer [explanation-to-str explanation-expr-to-str code-location]]))

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
Strings are represented with quotes.
```clojure
(fact
 (explanation-expr-to-str "foo" 3) => "\"foo\"")

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
The values of bound variables are printed.
```clojure
(fact (explanation-expr-to-str (atom `(foo/bar ~(atom 1) 2 3)) 3) => "(bar 1 2 ...)")

```
Unbound variables are printed as `_`.
```clojure
(fact (explanation-expr-to-str `(foo/bar ~(atom nil) 2 3) 3) => "(bar _ 2 ...)")

```
### Stringifying Explanations

The `explanation-to-str` function takes an explanation and returns a string representing
it. It takes an explanation term and return a string representing the explanation.

Strings are taken verbatim, joined with spaces.
```clojure
(fact
 (explanation-to-str ["foo" "bar"]) => "foo bar")

```
Non-strings are treated as s-expressions and are stringified with budget 3.
```clojure
(fact
 (explanation-to-str ["foo" 3 '(x/bar 1 2 3)]) => "foo 3 (bar 1 2 ...)")

```
## Code Location

$y_0$ tries to associate every explanation with a code location. Given an explanation, the
associated location will be the first location it encounters in a left-to-right traversal.

Code location for this purpose is carried as Clojure metadata and requires having at least
these two attributes: `:path` and `:row`.

The function `code-location` takes an explanation and returns its associated code location
or `nil` if one is not found.
```clojure
(fact
 (code-location ["does not contain meta" 
                 (with-meta '(does not contain path) {:row 2})
                 (with-meta '(does not contain row) {:path "x.y0"})
                 (with-meta '(a perfect match) {:path "z.y0" :row 3})
                 (with-meta '(the one who came too late) {:path "w.y0" :row 4})]) => {:path "z.y0" :row 3})

```
Sometimes it is necessary to dig deeper to find the location. The following example is
identical to the previous one, but has the whole thing encapsulated within a sequence. We
expect that `code-location` would dig the value by going inside.
```clojure
(fact
 (code-location [`("does not contain meta"
                   ~(with-meta '(does not contain path) {:row 2})
                   ~(with-meta '(does not contain row) {:path "x.y0"})
                   ~(with-meta '(a perfect match) {:path "z.y0" :row 3})
                   ~(with-meta '(the one who came too late) {:path "w.y0" :row 4}))]) => {:path "z.y0" :row 3})
```

