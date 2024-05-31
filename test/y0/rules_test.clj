(ns y0.rules-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.rules :refer [new-vars]]
            [y0.core :refer [all <-]]))

;; This module is responsible for rules and their interpretation.

;; ## Rule Parsing

;; The function `parse-rule` takes a predstore and an s-expression which represents a logic rule
;; and returns a status containing the predstore, with the rule added.

;; ## Variable Bindings

;; The term _variable binding_ refers to the matching between a symbol representing a variable
;; and the underlying variable (an `atom`). Variable bindings are represented programatically as
;; Clojure maps, with the symbols being keys and the atoms being the values.

;; In $y_0$, new variable bindings are introduced using vectors of symbols. Each symbol is
;; assigned a fresh variable (`(atom nil)`).

;; The function `new-vars` takes a binding and a vector of symbols and returns the binding updated
;; with the new fresh variables.
(fact
 (let [var-binding (new-vars {} '[foo bar baz])]
   (get var-binding 'foo) => #(instance? clojure.lang.Atom %)
   @(get var-binding 'foo) => nil?
   (get var-binding 'bar) => #(instance? clojure.lang.Atom %)
   @(get var-binding 'bar) => nil?
   (get var-binding 'baz) => #(instance? clojure.lang.Atom %)
   @(get var-binding 'baz) => nil?))

;; `new-vars` override any existing variables in the bindings.
(fact
 (let [var-binding (new-vars {'foo (atom 3)} '[foo])] 
   @(get var-binding 'foo) => nil?))
