(ns y0.explanation-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.explanation :refer [explanation-to-str explanation-expr-to-str
                                    code-location all-unique-locations
                                    expr-to-str]]
            [y0.location-util :refer [encode-file-pos]]
            [clojure.java.io :as io]
            [clojure.string :as str]))

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

;; ### Extracting Text

;; In languages which syntax is not not based on s-expressions, displaying the
;; parse-tree to the user is often not helpful. Instead, we would like to show
;; the relevant code.

;; `expr-to-str` takes a expression (parse-tree node) with location meta and
;; returns a string that represents the original code.

;; To do this, it first fetches the meta. Then, assuming there is a location,
;; it opens the file based on the `:path` and
;; [exctracts the span](location_util.md#extracting-based-on-code-location)
;; defined by `:start` and `:end`.
(fact
 (let [f (java.io.File/createTempFile "test" ".txt")]
   ;; Write some contents to a temp-file.
   (.deleteOnExit f)
   (spit f (str/join (System/lineSeparator) ["123456789 - 1"
                                             "123456789 - 2"
                                             "123456789 - 3"
                                             "123456789 - 4"
                                             "123456789 - 5"
                                             "123456789 - 6"]))
   (let [expr (with-meta [1 2 3] {:path (str f)
                                  :start (encode-file-pos 3 5)
                                  :end (encode-file-pos 3 8)})]
     (expr-to-str expr) => "567")

   ;; If the expression spans two lines, they are joined with a space.
   (let [expr (with-meta [1 2 3] {:path (str f)
                                  :start (encode-file-pos 3 5)
                                  :end (encode-file-pos 4 3)})]
     (expr-to-str expr) => "56789 - 3 12")
   ;; If the expression spans more than two lines, the first and last are
   ;; returned, separated by ...
   (let [expr (with-meta [1 2 3] {:path (str f)
                                  :start (encode-file-pos 3 5)
                                  :end (encode-file-pos 5 5)})]
     (expr-to-str expr) => "56789 - 3 ... 1234")))

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
;; these two attributes: `:path`, `:start` and `:end`.

;; The function `code-location` takes an explanation and returns its associated code location
;; or `nil` if one is not found.
(fact
 (code-location ["does not contain meta" 
                 (with-meta '(does not contain path) {:start 1000001 :end 1000002})
                 (with-meta '(does not contain start) {:path "x.y0" :end 1000002})
                 (with-meta '(does not contain end) {:path "x.y0" :start 1000001})
                 (with-meta '(a perfect match) {:path "z.y0" :start 1000001 :end 1000002})
                 (with-meta '(the one who came too late) {:path "w.y0" :start 2000001 :end 2000002})])
 => {:path "z.y0" :start 1000001 :end 1000002})

;; Sometimes it is necessary to dig deeper to find the location. The following example is
;; identical to the previous one, but has the whole thing encapsulated within a sequence. We
;; expect that `code-location` would dig the value by going inside.
(fact
 (code-location [`("does not contain meta"
                   ~(with-meta '(does not contain path) {:start 1000001 :end 1000002})
                   ~(with-meta '(does not contain start) {:path "x.y0" :end 1000002})
                   ~(with-meta '(does not contain end) {:path "x.y0" :start 1000001})
                   ~(with-meta '(a perfect match) {:path "z.y0" :start 1000001 :end 1000002})
                   ~(with-meta '(the one who came too late) {:path "w.y0" :start 2000001 :end 2000002}))])
 => {:path "z.y0" :start 1000001 :end 1000002})

;; Digging even deeper, code location can be found inside a bound variable.
(fact
 (code-location [(atom `(~(with-meta '(a perfect match) {:path "z.y0" :start 1000001 :end 1000002})))]) =>
 {:path "z.y0" :start 1000001 :end 1000002})

;; ## Extracting Terms

;; In addition to pointing the location that best matches the explanation, we
;; wish to also provide pointers to all the different locations referenced in
;; the explanation.

;; This is done using `all-unique-locations`. It takes an explanation and
;; returns a sequence of `[term location]` pairs, where the `term`s are a
;; subset of the terms in the explanation, each with its code location.
(fact
 (all-unique-locations ["This" (with-meta `x {:path "/foo/bar" :start 1 :end 4})
                        "is" (with-meta `y {:path "/foo/bar" :start 4 :end 6})
                        "a" (with-meta `z {:path "/foo/bar" :start 6 :end 9})
                        "test" (with-meta `w {:path "/foo/bar"  :start 9 :end 10})]) =>
 [[`x {:path "/foo/bar" :start 1 :end 4}]
  [`y {:path "/foo/bar" :start 4 :end 6}]
  [`z {:path "/foo/bar" :start 6 :end 9}]
  [`w {:path "/foo/bar"  :start 9 :end 10}]])

;; Terms without location are skipped.
(fact
 (all-unique-locations ["This" (with-meta `x {:path "/foo/bar" :start 1 :end 7})
                        "is" (with-meta `y {:start 4 :end 5})  ;; No :path
                        "a" (with-meta `z {:path "/foo/bar" :start 5})  ;; No :end
                        "test" (with-meta `w {:path "/foo/bar" :start 7 :end 9})]) =>
 [[`x {:path "/foo/bar" :start 1 :end 7}]
  [`w {:path "/foo/bar" :start 7 :end 9}]])

;; A full term is taken even if the location is only known by its internals.
(fact
 (all-unique-locations [["foo" (with-meta `x {:path "/foo/bar" :start 1 :end 7}) "bar"]]) =>
 [[["foo" `x "bar"] {:path "/foo/bar" :start 1 :end 7}]])

;; If one term is contained within another (i.e., they have the same `:path`
;; and the `:start` of the one is >= the `:start` of the other while the `:end`
;; of the one is <= the `:end` of the other), the internal term is removed.
(fact
 (all-unique-locations ["This" (with-meta `x {:path "/foo/bar" :start 1 :end 4})
                        "is" (with-meta `y {:path "/foo/bar" :start 2 :end 3})
                        "a" (with-meta `z {:path "/foo/bar" :start 6 :end 9})
                        "test" (with-meta `w {:path "/foo/bar"  :start 5 :end 10})]) =>
 [[`x {:path "/foo/bar" :start 1 :end 4}]
  [`w {:path "/foo/bar"  :start 5 :end 10}]])
