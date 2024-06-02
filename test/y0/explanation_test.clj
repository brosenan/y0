(ns y0.explanation-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.explanation :refer [explanation-to-str explanation-expr-to-str]]))

;; "Why not" explanations are an important aspect of $y_0$ and are here we describe the
;; software support for them.

;; ## Pretty-printing an Explanation

;; A "why not" explanation is a recursive structure. It is either a string, a sequence
;; representing an underlying s-expression or a vector of these. The `explanation-to-str`
;; function takes an explanation and returns a string representing it. It takes an
;; explanation term and a predstore and return a string representing the explanation.
;; The role of the predstore will be made clear later.

;; Given a string, the same string is returned.
(fact
 (explanation-to-str "foo bar" {}) => "foo bar")

;; ### Stringifying S-Expressions

;; The function `explanation-expr-to-str` takes an s-expression and a "budget" of elements
;; to be printed, and returns a string representing it.

;; A symbol is stringified without its namespace.
(fact (explanation-expr-to-str 'foo/bar 3) => "bar")

;; Numbers are stringified decimally.
(fact
 (explanation-expr-to-str 5 3) => "5"
 (explanation-expr-to-str 3.14 3) => "3.14")

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
