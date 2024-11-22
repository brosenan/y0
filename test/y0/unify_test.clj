(ns y0.unify-test
  (:require [midje.sweet :refer [fact => throws provided]]
            [y0.unify :refer :all]
            [y0.core :refer [&]]))

;; ## Unification

;; One of the most essential concepts of a logic programming language is _unification_.
;; Unification is best explained as solving an equation. Two terms, `A` and `B` are said
;; to be equal up to thhe values held by the variables they hold. The purpose of unification
;; is to find such a variable assignment that will make the terms equal, if one is possible.

;; The function `unify` takes two s-expressions and tries to unify them. It returns whether
;; they can be made of the same value.
(fact
 (unify 1 2) => false
 (unify 1 1) => true)

;; ### Variables

;; In $y_0$.clj, we use Clojure atoms as variables. A unification will therefore try  to `reset!`
;; these atoms to match the value on the other side of the equation.
(fact
 (let [x (atom nil)
       y (atom nil)]
   (unify 1 x) => true
   @x => 1
   (unify y 2) => true
   @y => 2))

;; ### Vectors and lists

;; When encountering a vector of a list on one side, the same structure with the same length
;; is expected on the other side.
(fact
 (let [x (atom nil)
       y (atom nil)]
   (unify [1 x] [y 2]) => true
   @x => 2
   @y => 1))

(fact
 (let [x (atom nil)
       y (atom nil)]
   (unify (list 1 x) (list y 2)) => true
   @x => 2
   @y => 1))

;; Unification fails for sequences of different lengths.
(fact
 (let [x (atom nil)
       y (atom nil)]
   (unify [1 x] [y 2 3]) => false))

(fact
 (let [x (atom nil)
       y (atom nil)]
   (unify (list 1 x 3) (list y 2)) => false))

;; Lists and vectors are considered different.
(fact
 (let [x (atom nil)
       y (atom nil)]
   (unify [1 x] (list y 2)) => false
   (unify (list y 2) [1 x]) => false))

;; #### List Deconstruction

;; A list of vector may end in `& term`. In such cases, `term` is unified against the rest of the terms
;; in the sequence on the other side.
(fact
 (let [h (atom nil)
       t (atom nil)]
   (unify [h 'y0.core/& t] [1 2 3]) => true
   @h => 1
   @t => [2 3])
 (let [h (atom nil)
       t (atom nil)]
   (unify [1 2 3] [h 'y0.core/& t]) => true
   @h => 1
   @t => [2 3])
 (let [x (atom nil)]
   (unify [1 'y0.core/& [2 3]] x) => true
   @x => [1 2 3])
 (let [x (atom nil)]
   (unify x [1 'y0.core/& [2 3]]) => true
   @x => [1 2 3]))


;; Specifically, the variable after the `&` can bind to an empty list.
(fact
 (let [h (atom nil)
       t (atom nil)]
   (unify [h 'y0.core/& t] [1]) => true
   @h => 1
   @t => [])
 (let [h (atom nil)
       t (atom nil)]
   (unify [1] [h 'y0.core/& t]) => true
   @h => 1
   @t => []))

;; The type of sequence (list vs. vector) is preserved. The tail of a list is a list and the tail of
;; a vector is a vector.
(fact
 (unify [1 'y0.core/& [2 3]] [1 2 3]) => true
 (unify '(1 y0.core/& (2 3)) '(1 2 3)) => true
 (unify [1 'y0.core/& '(2 3)] [1 2 3]) => false
 (unify '(1 y0.core/& [2 3]) '(1 2 3)) => false)

;; ### Bound Variables

;; Coming back to variables. So far we have seen variables (atoms) that are _unbound_,
;; meaning that their value was `nil` prior to unification and they appeared only once
;; in the terms being unified, so that once `unify` sets value to them, they are no
;; longer unbound.

;; In contract, _bound_ variables are atoms with non-`nil` values. In such cases,
;; `unify` treats them as the underlying values.

;; In the following example, the variable `x` is bound to `1`. Unifying it with another
;; value gives the same result as unifying `1` with that other value.
(fact
 (let [x (atom 1)]
   (unify x 1) => true
   (unify x 2) => false
   (unify 1 x) => true
   (unify 2 x) => false))

;; A variable can be bound to another variable. In such a case, unification should
;; traverse the reference chain and find the value at the top.
(fact
 (let [x (atom (atom (atom (atom 1))))]
   (unify x 1) => true
   (unify x 2) => false
   (unify 1 x) => true
   (unify 2 x) => false))

;; When unifying two variables, one binds to the other. Then, when unifying one of
;; them to a value, the other (indirectly) binds to the same value.

;; Note: The example below uses the function `reify-term` to find the value of
;; variables. This function is presented [later](#reification).
(fact
 (let [x (atom nil)
       y (atom nil)]
   (unify [x y] [y 1]) => true
   (reify-term x) => 1)
 
 ;; And the same example, on opposite sides of the unification.
 (let [x (atom nil)
       y (atom nil)]
   (unify [y 1] [x y]) => true
   (reify-term x) => 1))

;; ## Reification

;; The function `reify-term` reifies the terms it is given.

;; For scalar values, it just returns the given value.
(fact
 (reify-term 42) => 42)

;; In case of a variable (atom), a (deep) dereference is returned.
(fact
 (let [x (atom 7)]
   (reify-term x) => 7)
 (let [x (atom (atom (atom 7)))]
   (reify-term x) => 7))

;; Unbound variables remain unchanged.
(fact
 (let [x (atom nil)]
   (reify-term x) => x))

;; Empty lists remain lists.
(fact
 (reify-term ()) => seq?)

;; `reify-term` recurses into lists and vectors.
(fact
 (let [x (atom 3)]
   (reify-term [1 2 x]) => [1 2 3]
   (reify-term [1 2 x]) => vector?
   (reify-term (list 1 2 x)) => '(1 2 3)
   (reify-term (list 1 2 x)) => seq?))

;; A list or vector ending with `& something` are reified such that `something` is the
;; tail of the list or vector.
(fact
 (let [tail (atom [3 4 5])]
   (reify-term [1 2 'y0.core/& tail]) => [1 2 3 4 5]
   (reify-term [1 2 'y0.core/& tail]) => vector?
   (reify-term (list 1 2 'y0.core/& tail)) => '(1 2 3 4 5)
   (reify-term (list 1 2 'y0.core/& tail)) => seq?))

;; If the tail is not a list or a vector (e.g., an unbound variable or a symbol
;; representing one), the list or vector is not constructed.
(fact
 (let [tail (atom nil)]
   (reify-term [1 2 'y0.core/& tail]) => [1 2 'y0.core/& tail]
   (reify-term (list 1 2 'y0.core/& tail)) => (list 1 2 'y0.core/& tail)
   (reify-term [1 2 'y0.core/& 'tail]) => [1 2 'y0.core/& 'tail]
   (reify-term (list 1 2 'y0.core/& 'tail)) => (list 1 2 'y0.core/& 'tail)))

;; It also recurses into maps and sets.
(fact
 (let [x (atom 2)]
   (reify-term {:a 1
                :b x}) => {:a 1
                           :b 2}
   (reify-term #{1 x}) => #{1 2}))
