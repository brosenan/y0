(ns y0.explanation-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.explanation :refer [explanation-to-str explanation-expr-to-str code-location all-unique-locations]]))

;; "Why not" explanations are an important aspect of $y_0$ and are here we describe the
;; software support for them.

;; ## Pretty-printing an Explanation

;; A "why not" explanation is a vector of components, including strings and (other)
;; s-expressions. We begin by discussing how the s-expressions are being stringified.

;; ### Stringifying S-Expressions

;; The function `explanation-expr-to-str` takes an s-expression and a "budget" of elements
;; to be printed, and returns a string representing it.

;; A symbol is stringified without its namespace.
(fact (explanation-expr-to-str 'foo/bar 3) => "bar")

;; Numbers are stringified decimally.
(fact
 (explanation-expr-to-str 5 3) => "5"
 (explanation-expr-to-str 3.14 3) => "3.14")

;; Strings are represented with quotes.
(fact
 (explanation-expr-to-str "foo" 3) => "\"foo\"")

;; A sequence (form) is stringified by adding `()` around its contents and spaces between
;; its elements.
(fact (explanation-expr-to-str '(foo/bar 1 2) 3) => "(bar 1 2)")

;; If the number of elements in the sequence exceeds the budget, the remaining elements are
;; replaced with `...`.
(fact (explanation-expr-to-str '(foo/bar 1 2 3) 3) => "(bar 1 2 ...)")

;; Sub-expressions are taken with budget = 1.
(fact (explanation-expr-to-str '(foo/bar (+ 1 2) 3 4) 3) => "(bar (+ ...) 3 ...)")

;; Vectors are supported too.
(fact (explanation-expr-to-str '[[x y z] 1 [2 3] 4] 3) => "[[x ...] 1 [2 ...] ...]")

;; The values of bound variables are printed.
(fact (explanation-expr-to-str (atom `(foo/bar ~(atom 1) 2 3)) 3) => "(bar 1 2 ...)")

;; Unbound variables are printed as `_`.
(fact (explanation-expr-to-str `(foo/bar ~(atom nil) 2 3) 3) => "(bar _ 2 ...)")

;; Lists are constructed where necessary.
(fact (explanation-expr-to-str `(foo/bar 1 y0.core/& (2 3)) 5) => "(bar 1 2 3)")

;; ### Stringifying Explanations

;; The `explanation-to-str` function takes an explanation and returns a string representing
;; it. It takes an explanation term and return a string representing the explanation.

;; Strings are taken verbatim, joined with spaces.
(fact
 (explanation-to-str ["foo" "bar"]) => "foo bar")

;; Non-strings are treated as s-expressions and are stringified with budget 3.
(fact
 (explanation-to-str ["foo" 3 '(x/bar 1 2 3)]) => "foo 3 (bar 1 2 ...)")

;; ## Code Location

;; $y_0$ tries to associate every explanation with a code location. Given an explanation, the
;; associated location will be the first location it encounters in a left-to-right traversal.

;; Code location for this purpose is carried as Clojure metadata and requires having at least
;; these two attributes: `:path` and `:row`.

;; The function `code-location` takes an explanation and returns its associated code location
;; or `nil` if one is not found.
(fact
 (code-location ["does not contain meta" 
                 (with-meta '(does not contain path) {:row 2})
                 (with-meta '(does not contain row) {:path "x.y0"})
                 (with-meta '(a perfect match) {:path "z.y0" :row 3})
                 (with-meta '(the one who came too late) {:path "w.y0" :row 4})]) => {:path "z.y0" :row 3})

;; Sometimes it is necessary to dig deeper to find the location. The following example is
;; identical to the previous one, but has the whole thing encapsulated within a sequence. We
;; expect that `code-location` would dig the value by going inside.
(fact
 (code-location [`("does not contain meta"
                   ~(with-meta '(does not contain path) {:row 2})
                   ~(with-meta '(does not contain row) {:path "x.y0"})
                   ~(with-meta '(a perfect match) {:path "z.y0" :row 3})
                   ~(with-meta '(the one who came too late) {:path "w.y0" :row 4}))]) => {:path "z.y0" :row 3})

;; Digging even deeper, code location can be found inside a bound variable.
(fact
 (code-location [(atom `(~(with-meta '(a perfect match) {:path "z.y0" :row 3})))]) =>
 {:path "z.y0" :row 3})

;; ## Extracting Terms

;; In addition to pointing the location that best matches the explanation, we
;; wish to also provide pointers to all the different locations referenced in
;; the explanation.

;; This is done using `all-unique-locations`. It takes an explanation and
;; returns a sequence of `[term location]` pairs, where the `term`s are a
;; subset of the terms in the explanation, each with its code location.
(fact
 (all-unique-locations ["This" (with-meta `x {:row 3 :path "/foo/bar"})
                        "is" (with-meta `y {:row 4 :path "/foo/bar"})
                        "a" (with-meta `z {:row 5 :path "/foo/bar"})
                        "test" (with-meta `w {:row 2 :path "/foo/bar"})]) =>
 [[`x {:row 3 :path "/foo/bar"}]
  [`y {:row 4 :path "/foo/bar"}]
  [`z {:row 5 :path "/foo/bar"}]
  [`w {:row 2 :path "/foo/bar"}]])

;; Terms without location are skipped.
(fact
 (all-unique-locations ["This" (with-meta `x {:row 3 :path "/foo/bar"})
                        "is" (with-meta `y {:row 4})  ;; No :path
                        "a" (with-meta `z {:path "/foo/bar"})  ;; No :row
                        "test" (with-meta `w {:row 2 :path "/foo/bar"})]) =>
 [[`x {:row 3 :path "/foo/bar"}]
  [`w {:row 2 :path "/foo/bar"}]])

;; A full term is taken even if the location is only known by its internals.
(fact
 (all-unique-locations [["foo" (with-meta `x {:row 3 :path "/foo/bar"}) "bar"]]) =>
 [[["foo" `x "bar"] {:row 3 :path "/foo/bar"}]])

;; If two terms have the same location (`:path` and `:row`), only the first one
;; is returned.
(fact
 (all-unique-locations ["This" (with-meta `x {:row 3 :path "/foo/bar"})
                        "is" (with-meta `y {:row 4 :path "/foo/bar"})
                        "a" (with-meta `z {:row 3 :path "/foo/bar"})  ;; Same location as x
                        "test" (with-meta `w {:row 2 :path "/foo/bar"})
                        ])=>
 [[`x {:row 3 :path "/foo/bar"}]
  [`y {:row 4 :path "/foo/bar"}]
  [`w {:row 2 :path "/foo/bar"}]])

;; If the location map contains things other than `:row` and `:path`, they are
;; not taken into account in the dedupping.
(fact
 (all-unique-locations ["This" (with-meta `x {:row 3 :path "/foo/bar" :foo :bar})
                        "is" (with-meta `y {:row 4 :path "/foo/bar"})
                        "a" (with-meta `z {:row 3 :path "/foo/bar" :foo :baz})  ;; Same :row and :path as x
                        "test" (with-meta `w {:row 2 :path "/foo/bar"})]) =>
 [[`x {:row 3 :path "/foo/bar" :foo :bar}]
  [`y {:row 4 :path "/foo/bar"}]
  [`w {:row 2 :path "/foo/bar"}]])
