(ns y0.predstore-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.predstore :refer [pred-key arg-key]]
            [y0.unify :refer [&]]))

;; ## Goal Keys

;; For goals to be evaluated efficiently, there needs to be a match goals with corresponding definitions.
;; In y0, a definition refers to a certain predicate, with or without a specific pattern for its first argument.

;; Predicates are identified with a `:name` and an `:arity`. The `pred-key` function takes a goal and returns
;; a corresponding _predicate key_ consisting of these two attributes.

(fact
 (pred-key '(foo 1 2 3)) => {:name "foo" :arity 3})

;; ### Argument Keys

;; Within a single predicate, goals are keyed by the first argument. The function `arg-key` returns a key
;; based on the value of the first argument.

;; Scalar values are keyed using the `:value` attribute.
(fact
 (arg-key 42) => {:value 42}
 (arg-key "foo") => {:value "foo"})

;; Symbols and keywords are keyed using the `:symbol` and `:keyword` attributes, respetively.
(fact
 (arg-key 'foo) => {:symbol "foo"}
 (arg-key :foo) => {:keyword ":foo"})

;; Unbount variables can be bound to anything and are therefore represented by `{}`, an empty key.
(fact
 (arg-key (atom nil)) => {})

;; #### Lists and Forms

;; An empty list is represented as `{:list :empty}`.
(fact
 (arg-key '()) => {:list :empty})

;; A pattern matching a non-empty list is represented by `{:list :non-empty}`
(fact
 (arg-key (list (atom nil) & (atom nil))) => {:list :non-empty})

;; If the length of the list is known, the value of `:list` becomes the length.
(fact
 (arg-key (list (atom nil) (atom nil) (atom nil))) => {:list 3})

;; The key of the first element in the list is merged with the list's key, to give a key for the completed _form_.
(fact
 (arg-key (list 'foo (atom nil) (atom nil))) => {:list 3 :symbol "foo"}
 (arg-key (list :foo (atom nil))) => {:list 2 :keyword ":foo"}
 (arg-key (list 42 & (atom nil))) => {:list :non-empty :value 42})

;; Vectors are similar to lists, but use the `:vec` attribute rather than `:list`.
(fact
 (arg-key []) => {:vec :empty}
 (arg-key [(atom nil) & (atom nil)]) => {:vec :non-empty}
 (arg-key [(atom nil) (atom nil) (atom nil)]) => {:vec 3}
 (arg-key ['foo (atom nil) (atom nil)]) => {:vec 3 :symbol "foo"})

