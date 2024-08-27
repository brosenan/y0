(ns y0.term-utils-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.term-utils :refer [postwalk-with-meta postwalk-meta 
                                   replace-vars ground? replace-ground-vars]]))

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
                        (with-meta '() {:seq :empty})
                        (with-meta {6 [7] 8 [9]} {:map :bar})
                        (with-meta #{[10] 11} {:set :baz})]
              {:vec :top})
       res (postwalk-with-meta #(if (int? %)
                                  (inc %)
                                  %) tree)]
   res => [2 [3 4] '(5 6) '() {7 [8] 9 [10]} #{[11] 12}]
   (-> res meta) => {:vec :top}
   (-> res second meta) => {:vec :bottom}
   (-> res (nth 2) meta) => {:seq :foo}
   (-> res (nth 3) meta) => {:seq :empty}
   (-> res (nth 4) meta) => {:map :bar}
   (-> res (nth 5) meta) => {:set :baz}))

;; ### A `postwalk` for Meta

;; A slightly different operation is to transform the meta properties of a
;; s-expression. `postwalk-with-meta` cannot be used in this case because it
;; would override any changes to the meta done by the function.

;; `postwalk-meta` takes a function and a s-expression. The function, rather
;; than being called on the object, is being called on the _meta_ of each
;; object. Then, the returned value from the function is installed as the new
;; meta.

;; In the following example we use `postwalk-meta` to add a meta property
;; `:foo` with value `:bar` to all nodes in a s-expression.
(fact
 (let [tree '[foo (#{baz} {baz 42})]
       res (postwalk-meta #(assoc % :foo :bar) tree)]
   res => tree
   (-> res meta :foo) => :bar
   (-> res first meta :foo) => :bar
   (-> res second meta :foo) => :bar
   (-> res second first meta :foo) => :bar
   (-> res second first first meta :foo) => :bar
   (-> res second second first first meta :foo) => :bar))

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

;; ## Ground Terms

;; Ground terms are terms that do not contain free variables.

;; The function `ground?` takes a term and returns whether or ont it is ground.
(fact
 (ground? 42) => true)

;; A free variable (`atom` containing `nil`) is not ground.
(fact
 (ground? (atom nil)) => false)

;; For a bound variable, if the term it is bound to is ground, the variable is
;; ground.
(fact
 (ground? (atom 42)) => true
 (ground? (atom [1 (atom nil) 3])) => false)

;; For a collection type, the collection is ground if and only if all its
;; elements are ground.
(fact
 (ground? [1 2 3]) => true
 (ground? [1 (atom nil) 3]) => false
 (ground? [1 #{(atom nil) 2} 3]) => false
 (ground? [1 #{2} 3]) => true)

;; `replace-ground-vars` takes a symbolic term and a variable map. It returns
;; the term and the var-map after replacing all variables bound to ground terms
;; in the term with their values, and removing these variables from the
;; var-map.

;; If all the variables in the var-map are unbound, nothing happens.
(fact
 (let [x (atom nil)
       y (atom nil)]
   (replace-ground-vars `(hello x y) {`x x `y y}) =>
   [`(hello x y) {`x x `y y}]))

;; Variables that are bound to ground terms are replaced in the term and
;; removed from the map.
(fact
 (let [x (atom [1 2 (atom nil)])
       y (atom [1 2 3])]
   (replace-ground-vars `(hello x y) {`x x `y y}) =>
   [`(hello x [1 2 3]) {`x x}]))