  * [Pretty-printing-an-Explanation](#pretty-printing-an-explanation)
    * [Stringifying S-Expressions](#stringifying-s-expressions)
    * [Extracting Text](#extracting-text)
    * [Stringifying Explanations](#stringifying-explanations)
  * [Code Location](#code-location)
  * [Extracting Terms](#extracting-terms)
```clojure
(ns y0.explanation-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.explanation :refer [explanation-to-str explanation-expr-to-str
                                    code-location all-unique-locations
                                    extract-expr-text *stringify-expr*
                                    *create-reader*]]
            [y0.location-util :refer [encode-file-pos]]
            [clojure.java.io :as io]
            [clojure.string :as str]))

```
"Why not" explanations are an important aspect of $y_0$ and are here we
describe the software support for them.

## Pretty-printing an Explanation

A "why not" explanation is a vector of components, including strings and
(other) s-expressions. We begin by discussing how the s-expressions are being
stringified.

### Stringifying S-Expressions

The function `explanation-expr-to-str` takes an s-expression and a "budget"
of elements to be printed, and returns a string representing it.

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
A sequence (form) is stringified by adding `()` around its contents and
spaces between its elements.
```clojure
(fact (explanation-expr-to-str '(foo/bar 1 2) 3) => "(bar 1 2)")

```
If the number of elements in the sequence exceeds the budget, the remaining
elements are replaced with `...`.
```clojure
(fact (explanation-expr-to-str '(foo/bar 1 2 3) 3) => "(bar 1 2 ...)")

```
Sub-expressions are taken with budget = 1.
```clojure
(fact (explanation-expr-to-str '(foo/bar (+ 1 2) 3 4) 3) =>
      "(bar (+ ...) 3 ...)")

```
Vectors are supported too.
```clojure
(fact (explanation-expr-to-str '[[x y z] 1 [2 3] 4] 3) =>
      "[[x ...] 1 [2 ...] ...]")

```
The values of bound variables are printed.
```clojure
(fact (explanation-expr-to-str (atom `(foo/bar ~(atom 1) 2 3)) 3) =>
      "(bar 1 2 ...)")

```
Unbound variables are printed as `_`.
```clojure
(fact (explanation-expr-to-str `(foo/bar ~(atom nil) 2 3) 3) => "(bar _ 2 ...)")

```
Lists are constructed where necessary.
```clojure
(fact (explanation-expr-to-str `(foo/bar 1 y0.core/& (2 3)) 5) => "(bar 1 2 3)")

```
### Extracting Text

In languages which syntax is not not based on s-expressions, displaying the
parse-tree to the user is often not helpful. Instead, we would like to show
the relevant code.

`extract-expr-text` takes a expression (parse-tree node) with location meta
and returns a string that represents the original code.

To do this, it first fetches the meta. Then, assuming there is a location,
it opens the file based on the `:path` and
[exctracts the span](location_util.md#extracting-based-on-code-location)
defined by `:start` and `:end`.

Because a file is not always represented on disk (as in the following tests),
we define `*create-reader*`, a function that takes a path and returns a
reader. `*create-reader*` defaults to `io/reader`.
```clojure
(fact
 *create-reader* => #(= % io/reader))

```
The following function generates a function that can be bound to
`*create-reader*` in tests. It takes an expected path and a string and
returns a function that, given the same path, returns a reader based on the
contents of the string.
```clojure
(defn- create-reader-fake [expected-path s]
  (fn [path]
    (if (= path expected-path)
      (-> s (.getBytes "utf8") io/reader)
      (throw (Exception. (str "Attempt to open unexpected path " path))))))

(fact
 (binding [*create-reader*
           (create-reader-fake "/path/to/file.txt"
                               (str/join (System/lineSeparator)
                                         ["123456789 - 1"
                                          "123456789 - 2"
                                          "123456789 - 3"
                                          "123456789 - 4"
                                          "123456789 - 5"
                                          "123456789 - 6"]))]
   (let [expr (with-meta [1 2 3] {:path "/path/to/file.txt"
                                  :start (encode-file-pos 3 5)
                                  :end (encode-file-pos 3 8)})]
     (extract-expr-text expr) => "567")

   ;; If the expression spans two lines, they are joined with a space.
   (let [expr (with-meta [1 2 3] {:path "/path/to/file.txt"
                                  :start (encode-file-pos 3 5)
                                  :end (encode-file-pos 4 3)})]
     (extract-expr-text expr) => "56789 - 3 12")
   ;; If the expression spans more than two lines, the first and last are
   ;; returned, separated by ...
   (let [expr (with-meta [1 2 3] {:path "/path/to/file.txt"
                                  :start (encode-file-pos 3 5)
                                  :end (encode-file-pos 5 5)})]
     (extract-expr-text expr) => "56789 - 3 ... 1234")))

```
If the lines being shown contain spaces beyond a single space, they are
reduced to a single space.
```clojure
(fact
 (binding [*create-reader*
           (create-reader-fake "/path/to/file.txt"
                               (str/join (System/lineSeparator)
                                         ["123456789 - 1"
                                          "123456789 - 2"
                                          "123456789 \t - 3"
                                          "123456789 - 4"
                                          " \t 456789 - 5"
                                          "123456789 - 6"]))]
   (let [expr (with-meta [1 2 3] {:path "/path/to/file.txt"
                                  :start (encode-file-pos 3 5)
                                  :end (encode-file-pos 5 5)})]
     (extract-expr-text expr) => "56789 - 3 ... 4")))

```
Whitespace is removed from the beginning and end of the string.
```clojure
(fact
 (binding [*create-reader*
           (create-reader-fake "/path/to/file.txt"
                               (str/join (System/lineSeparator)
                                         ["123456789 - 1"
                                          "123456789 - 2"
                                          "1234  789 \t - 3"
                                          "123456789 - 4"
                                          "1   56789 - 5"
                                          "123456789 - 6"]))]
   (let [expr (with-meta [1 2 3] {:path "/path/to/file.txt"
                                  :start (encode-file-pos 3 5)
                                  :end (encode-file-pos 5 5)})]
     (extract-expr-text expr) => "789 - 3 ... 1")))

```
Empty lines / lines with only whitespace are ignored on both ends.
```clojure
(fact
 (binding [*create-reader*
           (create-reader-fake "/path/to/file.txt"
                               (str/join (System/lineSeparator)
                                         ["123456789 - 1"
                                          "  \t  "
                                          "123456789 \t - 3"
                                          "123456789 - 4"
                                          " \t   "
                                          "123456789 - 6"]))]
   (let [expr (with-meta [1 2 3] {:path "/path/to/file.txt"
                                  :start (encode-file-pos 2 1)
                                  :end (encode-file-pos 6 5)})]
     (extract-expr-text expr) => "123456789 - 3 ... 1234")))

```
If `extract-expr-text` is given a list or a vector without a location, it
recurs to the underlying elements and joins them with commas.
```clojure
(fact
 (binding [*create-reader*
           (create-reader-fake "/path/to/file.txt"
                               (str/join (System/lineSeparator)
                                         ["123456789 - 1"
                                          "123456789 - 2"
                                          "123456789 - 3"
                                          "123456789 - 4"
                                          "123456789 - 5"
                                          "123456789 - 6"]))]
   (let [expr [(with-meta [1 2 3] {:path "/path/to/file.txt"
                                   :start (encode-file-pos 3 5)
                                   :end (encode-file-pos 3 8)})
               (with-meta [1 2 3] {:path "/path/to/file.txt"
                                   :start (encode-file-pos 4 2)
                                   :end (encode-file-pos 4 5)})]]
     (extract-expr-text expr) => "567, 234")))

```
If it is not a vector or a list, it stringifies the given object using
`explanation-expr-to-str`.
```clojure
(fact
 (extract-expr-text "foo") => "\"foo\"")

```
### Stringifying Explanations

The `explanation-to-str` function takes an explanation and returns a string
representing it. It takes an explanation term and return a string
representing the explanation.

Strings are taken verbatim, joined with spaces.
```clojure
(fact
 (explanation-to-str ["foo" "bar"]) => "foo bar")

```
By default, non-strings are treated as s-expressions and are stringified
using `explanation-expr-to-str` with budget 3.
```clojure
(fact
 (explanation-to-str ["foo" 3 '(x/bar 1 2 3)]) => "foo 3 (bar 1 2 ...)")

```
The `*stringify-expr*` dynamic variable controls the stringification, so
other strategies can be bound.
```clojure
(fact
 (binding [*stringify-expr* pr-str]
   (explanation-to-str ["foo" 3 '(x/bar 1 2 3)]) => "foo 3 (x/bar 1 2 3)"))

```
This can be used to bind `extract-expr-text`, which we will not demonstrate here.

## Code Location

$y_0$ tries to associate every explanation with a code location. Given an
explanation, the associated location will be the first location it encounters
in a left-to-right traversal.

Code location for this purpose is carried as Clojure metadata and requires
having at least these two attributes: `:path`, `:start` and `:end`.

The function `code-location` takes an explanation and returns its associated
code location or `nil` if one is not found.
```clojure
(fact
 (code-location ["does not contain meta" 
                 (with-meta '(does not contain path) {:start 1000001 :end 1000002})
                 (with-meta '(does not contain start) {:path "x.y0" :end 1000002})
                 (with-meta '(does not contain end) {:path "x.y0" :start 1000001})
                 (with-meta '(a perfect match) {:path "z.y0" :start 1000001 :end 1000002})
                 (with-meta '(the one who came too late) {:path "w.y0" :start 2000001 :end 2000002})])
 => {:path "z.y0" :start 1000001 :end 1000002})

```
Sometimes it is necessary to dig deeper to find the location. The following
example is identical to the previous one, but has the whole thing
encapsulated within a sequence. We expect that `code-location` would dig the
value by going inside.
```clojure
(fact
 (code-location [`("does not contain meta"
                   ~(with-meta '(does not contain path) {:start 1000001 :end 1000002})
                   ~(with-meta '(does not contain start) {:path "x.y0" :end 1000002})
                   ~(with-meta '(does not contain end) {:path "x.y0" :start 1000001})
                   ~(with-meta '(a perfect match) {:path "z.y0" :start 1000001 :end 1000002})
                   ~(with-meta '(the one who came too late) {:path "w.y0" :start 2000001 :end 2000002}))])
 => {:path "z.y0" :start 1000001 :end 1000002})

```
Digging even deeper, code location can be found inside a bound variable.
```clojure
(fact
 (code-location [(atom `(~(with-meta '(a perfect match) {:path "z.y0" :start 1000001 :end 1000002})))]) =>
 {:path "z.y0" :start 1000001 :end 1000002})

```
## Extracting Terms

In addition to pointing the location that best matches the explanation, we
wish to also provide pointers to all the different locations referenced in
the explanation.

This is done using `all-unique-locations`. It takes an explanation and
returns a sequence of `[term location]` pairs, where the `term`s are a
subset of the terms in the explanation, each with its code location.
```clojure
(fact
 (all-unique-locations ["This" (with-meta `x {:path "/foo/bar" :start 1 :end 4})
                        "is" (with-meta `y {:path "/foo/bar" :start 4 :end 6})
                        "a" (with-meta `z {:path "/foo/bar" :start 6 :end 9})
                        "test" (with-meta `w {:path "/foo/bar"  :start 9 :end 10})]) =>
 [[`x {:path "/foo/bar" :start 1 :end 4}]
  [`y {:path "/foo/bar" :start 4 :end 6}]
  [`z {:path "/foo/bar" :start 6 :end 9}]
  [`w {:path "/foo/bar"  :start 9 :end 10}]])

```
Terms without location are skipped.
```clojure
(fact
 (all-unique-locations ["This" (with-meta `x {:path "/foo/bar" :start 1 :end 7})
                        "is" (with-meta `y {:start 4 :end 5})  ;; No :path
                        "a" (with-meta `z {:path "/foo/bar" :start 5})  ;; No :end
                        "test" (with-meta `w {:path "/foo/bar" :start 7 :end 9})]) =>
 [[`x {:path "/foo/bar" :start 1 :end 7}]
  [`w {:path "/foo/bar" :start 7 :end 9}]])

```
A full term is taken even if the location is only known by its internals.
```clojure
(fact
 (all-unique-locations [["foo" (with-meta `x {:path "/foo/bar" :start 1 :end 7}) "bar"]]) =>
 [[["foo" `x "bar"] {:path "/foo/bar" :start 1 :end 7}]])

```
If one term is contained within another (i.e., they have the same `:path`
and the `:start` of the one is >= the `:start` of the other while the `:end`
of the one is <= the `:end` of the other), the internal term is removed.
```clojure
(fact
 (all-unique-locations ["This" (with-meta `x {:path "/foo/bar" :start 1 :end 4})
                        "is" (with-meta `y {:path "/foo/bar" :start 2 :end 3})
                        "a" (with-meta `z {:path "/foo/bar" :start 6 :end 9})
                        "test" (with-meta `w {:path "/foo/bar"  :start 5 :end 10})]) =>
 [[`x {:path "/foo/bar" :start 1 :end 4}]
  [`w {:path "/foo/bar"  :start 5 :end 10}]])
```

