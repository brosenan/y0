(ns y0.predstore-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.predstore :refer [pred-key arg-key arg-key-generalizations pd-store-rule]]
            [y0.core :refer [& specific-rule-without-base must-come-before conflicting-defs]]
            [y0.status :refer [ok ->s]]))

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

;; ### Argument Key Generalization

;; Consider the goal `(mypred (foo 1 2 3) x)`. It is certainly a call to the predicate `{:name "mypred" :arity 2}`.
;; However, depending on which rules exist for this predicate, it may match one of a few options:

;; 1. `(mypred (foo x y z) r)`, where `x`, `y`, `z` and `r` are all variables,
;; 2. `(mypred (foo & args) r)` or some other variadic form,
;; 3. `(mypred (head & tail) r)`, where `head`, `tail` and `r` are variables, or
;; 4. `(mypred x y)`, where `x` and `y` are variables.

;; Note that `(mypred (w x y z))` for variables `w`, `x`, `y` and `z` is not considered a match. This is because
;; if it were, there would be a conflict with option (2). In y0 we explicitly treat the first argument of a predicate
;; as a form, i.e., we prioritize its name (the first element) over its size, when treated as a list.

;; To allow matching against all these options, the function `arg-key-generalizations` takes an arg-key and returns
;; a lazy sequence of "generalizations", starting from the key itself, moving to keys that are more and more general,
;; until reaching the most general key `{}`, which matches anything.

;; For `{}`, the generalization sequence only contains `{}`.
(fact
 (arg-key-generalizations {}) => [{}])

;; For a key containing a `:non-empty` list, the sequence contains the key and `{}`.
(fact
 (arg-key-generalizations {:list :non-empty}) => [{:list :non-empty} {}])

;; A key containing a `:symbol`, `:keyword` or `:value` first removes these attributes from the key before removing the
;; `:list` attribute.
(fact
 (arg-key-generalizations {:list :non-empty :symbol "foo"}) => [{:list :non-empty :symbol "foo"}
                                                                {:list :non-empty}
                                                                {}]
 (arg-key-generalizations {:list :non-empty :keyword ":foo"}) => [{:list :non-empty :keyword ":foo"}
                                                                  {:list :non-empty}
                                                                  {}]
 (arg-key-generalizations {:list :non-empty :value 42}) => [{:list :non-empty :value 42}
                                                            {:list :non-empty}
                                                            {}])

;; A fixed-size list will first generalize into a variadic list (`:list :non-empty`) before generalizing the first element.
(fact
 (arg-key-generalizations {:list 3 :symbol "foo"}) => [{:list 3 :symbol "foo"}
                                                       {:list :non-empty :symbol "foo"}
                                                       {:list :non-empty}
                                                       {}])

;; Vectors move the same path as lists.
(fact
 (arg-key-generalizations {:vec 3 :symbol "foo"}) => [{:vec 3 :symbol "foo"}
                                                      {:vec :non-empty :symbol "foo"}
                                                      {:vec :non-empty}
                                                      {}])

;; ## Storing Predicates Rules

;; The predicate store is a map of maps. The main map (predicate store) is keyed by a [predicate key](#goal-key) while
;; the inner maps (predicate definitions) are keyd by [argument keys](#argument-keys). We will discuss predicate
;; definitions first and then the predicate store.

;; ### Predicate Definitions

;; The function `pd-store-rule` takes a predicate definition, the rule's _head_ and the body of the rule in the form
;; of a function. It returns a [status](status.md) containing the updated predicate definition or an explanation why
;; adding it wasn't possible.

;; Given a free variable as a first argument and an empty definition, the rule is added.
(fact
 (->s (pd-store-rule {} (list 'my-pred (atom nil) (atom nil) 7) (constantly 42))
      (ok get {})
      (ok apply []))=> {:ok 42})

;; If instead of a free variable, we provide anything else as a first argument in the head (on an empty definition),
;; we get an error.
(fact
 (let [x (atom nil)]
   (pd-store-rule {} `(my-pred :foo ~x 7) (constantly 42)) >
   {:err `(specific-rule-without-base (my-pred :foo ~x 7))}))

;; A specific rule (one with anything other than an unbound var as its first argument) may follow a "base" rule.
(fact
 (->s (ok {})
      (pd-store-rule `(my-pred ~(atom nil) ~(atom nil) 7) (constantly 42))
      (pd-store-rule `(my-pred :foo ~(atom nil) 7) (constantly 43))
      (ok get {:keyword ":foo"})
      (ok apply [])) => {:ok 43})

;; Head patterns that overlap need to be added in order, from the most general to the most specific.
(fact
 (->s (ok {})
      (pd-store-rule `(my-pred ~(atom nil) ~(atom nil) 7) (constantly 42))
      (pd-store-rule `(my-pred (foo ~(atom nil) & ~(atom nil)) ~(atom nil) 7) (constantly 43))
      (pd-store-rule `(my-pred (foo ~(atom nil) ~(atom nil)) ~(atom nil) 7) (constantly 44))
      (ok get {:list 3 :symbol "y0.predstore-test/foo"})
      (ok apply [])) => {:ok 44})

;; When this order is violated, however, an error is returned.
(fact
 (let [x (atom nil)
       y (atom nil)
       z (atom nil)]
   (->s (ok {})
        (pd-store-rule `(my-pred ~x ~z 7) (constantly 42))
        (pd-store-rule `(my-pred (foo ~x ~y) ~z 7) (constantly 44))
        (pd-store-rule `(my-pred (foo ~x & ~y) ~z 7) (constantly 43)))
   => {:err `(must-come-before (my-pred (foo ~x & ~y) ~z 7)
                               (my-pred (foo ~x ~y) ~z 7))}))

;; And of course, if two rules have the exact same first-arg pattern, this is a conflict.
(fact
 (let [x (atom nil)
       y (atom nil)
       z (atom nil)]
   (->s (ok {})
        (pd-store-rule `(my-pred ~x ~z 7) (constantly 42))
        (pd-store-rule `(my-pred (foo ~x ~y) ~z 7) (constantly 44))
        (pd-store-rule `(my-pred (foo ~x ~z) ~y 8) (constantly 43)))
   => {:err `(conflicting-defs (my-pred (foo ~x ~z) ~y 8)
                               (my-pred (foo ~x ~y) ~z 7))}))