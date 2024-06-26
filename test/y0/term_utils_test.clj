(ns y0.term-utils-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.term-utils :refer [postwalk-with-meta replace-vars]]))

;; # Term Utils

;; This module contains utility functions for dealing with terms (s-expression) while
;; maintaining their source location, held as Clojure metadata.

;; ## Meta-Preserving `postwalk`.

;; In the process of renaming symbols to their global namespaces we need to traverse
;; the entire module. This is easy to do using `clojure.walk/postwalk`. Unfortunately,
;; this function does not preserve metadata on the objects it traverses. For this
;; reason, we create `postwalk-with-meta`, which, similar to `postwalk`, takes a function
;; and an s-expression and traverses the expression, calling the function on every node,
;; replacing it with the return value. However, unlike `postwalk`, it preseves metadata.

;; First we demonstrate this for a simple object.
(fact
 (let [res (postwalk-with-meta identity (with-meta [] {:foo :bar}))]
   res => []
   (meta res) => {:foo :bar})
 (let [res (postwalk-with-meta (constantly 'x) (with-meta [] {:foo :bar}))]
   res => 'x
   (meta res) => {:foo :bar}))

;; If the expression cannot hold metadata, none is passed on.
(fact
 (let [res (postwalk-with-meta (constantly 'x) 42)]
   res => 'x
   (meta res) => nil))

;; In the following example we show how postwalk-with-meta traverses a tree of lists
;; and vectors, containing numbers as leafs. The function we provide will increment
;; the numbers. Metadata on the lists and vectors will be preserved.
(fact
 (let [tree (with-meta [1 
                        (with-meta [2 3] {:vec :bottom})
                        (with-meta '(4 5) {:seq :foo})
                        (with-meta {6 [7] 8 [9]} {:map :bar})
                        (with-meta #{[10] 11} {:set :baz})]
              {:vec :top})
       res (postwalk-with-meta #(if (int? %)
                                  (inc %)
                                  %) tree)]
   res => [2 [3 4] '(5 6) {7 [8] 9 [10]} #{[11] 12}]
   (-> res meta) => {:vec :top}
   (-> res second meta) => {:vec :bottom}
   (-> res (nth 2) meta) => {:seq :foo}
   (-> res (nth 3) meta) => {:map :bar}
   (-> res (nth 4) meta) => {:set :baz}))

;; ## Replacing Variables in Terms

;; In $y_0$'s Clojure implementation we often replace symbols in a term by
;; the variables they represent. The function `replace-vars` takes a term and a
;; map mapping variable names (symbols) to their values (atoms) and returns
;; the term with the symbols replaced.

;; As a trivial case, if the term is `nil`, `nil` is returned.
(fact
 (replace-vars nil {}) => nil)

;; As another trivial case, if the given term does not contain any of the
;; variables in the map, the term is returned unchanged.
(fact
 (replace-vars `(foo x y) {`z (atom nil)}) => `(foo x y))

;; If the term does contain variables, they are replaced by their meaning in
;; the map.
(fact
 (let [x (atom nil)
       y (atom nil)]
   (replace-vars `(foo x y) `{x ~x
                              y ~y}) => `(foo ~x ~y)))

;; `replace-vars` preserves metadata on the term.
(fact
 (let [x (atom nil)
       y (atom nil)
       term `(foo x y)
       term (with-meta term {:foo :bar})
       term (replace-vars term `{x ~x
                                 y ~y})]
    (meta term) => {:foo :bar}))
