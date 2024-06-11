(ns y0.predstore-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.predstore :refer [pred-key arg-key arg-key-generalizations
                                  pd-store-rule pd-match store-rule match-rule generalize-arg]]
            [y0.core :refer [& specific-rule-without-base must-come-before conflicting-defs undefined-predicate]]
            [y0.status :refer [ok ->s]]))

;; ## Goal Keys

;; For goals to be evaluated efficiently, there needs to be a match goals with corresponding definitions.
;; In $y_0$, a definition refers to a certain predicate, with or without a specific pattern for its first argument.

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

;; A bound variable, however, is represented by the underlying value.
(fact
 (arg-key (atom 'foo)) => {:symbol "foo"})

;; #### Lists and Forms

;; An empty list is represented as `{:list :empty}`.
(fact
 (arg-key '()) => {:list :empty})

;; A pattern matching a non-empty list is represented by `{:list :non-empty}`
(fact
 (arg-key `(~(atom nil) y0.core/& ~(atom nil))) => {:list :non-empty})

;; If the length of the list is known, the value of `:list` becomes the length.
(fact
 (arg-key `(~(atom nil) ~(atom nil) ~(atom nil))) => {:list 3})

;; The key of the first element in the list is merged with the list's key, to give a key for the completed _form_.
(fact
 (arg-key `(foo ~(atom nil) ~(atom nil))) => {:list 3 :symbol "y0.predstore-test/foo"}
 (arg-key `(foo y0.core/& ~(atom nil))) => {:list :non-empty :symbol "y0.predstore-test/foo"}
 (arg-key `(:foo ~(atom nil))) => {:list 2 :keyword ":foo"}
 (arg-key `(42 y0.core/& ~(atom nil))) => {:list :non-empty :value 42})

;; Vectors are similar to lists, but use the `:vec` attribute rather than `:list`.
(fact
 (arg-key []) => {:vec :empty}
 (arg-key [(atom nil) 'y0.core/& (atom nil)]) => {:vec :non-empty}
 (arg-key [(atom nil) (atom nil) (atom nil)]) => {:vec 3}
 (arg-key ['foo (atom nil) (atom nil)]) => {:vec 3 :symbol "foo"})

;; ### Argument Key Generalization

;; Consider the goal `(mypred (foo 1 2 3) x)`. It is certainly a call to the predicate `{:name "mypred" :arity 2}`.
;; However, depending on which rules exist for this predicate, it may match one of a few options:

;; 1. `(mypred (foo x y z) r)`, where `x`, `y`, `z` and `r` are all variables,
;; 2. `(mypred (foo & args) r)` or some other variadic form,
;; 3. `(mypred (w x y z) r)`, where where `x`, `y`, `z`, `w` and `r` are variables, or
;; 4. `(mypred (head & tail) r)`, where `head`, `tail` and `r` are variables, or
;; 5. `(mypred x y)`, where `x` and `y` are variables.

;; The function `generalize-arg` takes an arg-key and returns a vector with all the direct generalizations of this
;; key.

;; A `{}` is already the most general and therefor does not have any generalizations.
(fact
 (generalize-arg {}) => [])

;; For a key containing `:symbol`, `:keyword` or `:value`, the marker (a key within a key) is removed.
;; In the following example we provide a key containing all these markers (not a real example) and show that
;; the result is a list containing copies of this key, each time with a different marker removed.
(fact
 (generalize-arg {:symbol "foo"
                  :keyword ":foo"
                  :value 42}) => [{:keyword ":foo"
                                   :value 42}
                                  {:symbol "foo"
                                   :value 42}
                                  {:symbol "foo"
                                   :keyword ":foo"}])

;; Lists and vectors with a known size are generalized to `:non-empty`.
(fact
 (generalize-arg {:list 3
                  :vec 4}) => [{:list :non-empty
                                :vec 4}
                               {:list 3
                                :vec :non-empty}])

;; Lists and vectors that are already `:non-empty` are removed, but only as long as there are no other markers in the
;; key.
(fact
 (generalize-arg {:list :non-empty}) => [{}]
 (generalize-arg {:vec :non-empty}) => [{}]
 (generalize-arg {:list :non-empty
                  :something :else}) => []
 (generalize-arg {:vec :non-empty
                  :something :else}) => [])

;; The function `arg-key-generalizations` uses `generalize-arg` to create a sequence of all (transitive) generalizations
;; of a given key.

(fact
 (arg-key-generalizations {:list 3 :symbol "foo"}) => [{:list 3 :symbol "foo"}
                                                       {:list 3}
                                                       {:list :non-empty :symbol "foo"}
                                                       {:list :non-empty}
                                                       {}]
 (arg-key-generalizations {:vec 3 :symbol "foo"}) => [{:vec 3 :symbol "foo"}
                                                      {:vec 3}
                                                      {:vec :non-empty :symbol "foo"}
                                                      {:vec :non-empty}
                                                      {}])

;; ## Storage and Retreival Predicates Rules

;; The predicate store is a map of maps. The main map (predicate store) is keyed by a [predicate key](#goal-key) while
;; the inner maps (predicate definitions) are keyd by [argument keys](#argument-keys). We will discuss predicate
;; definitions first and then the predicate store.

;; ### Predicate Definitions

;; The function `pd-store-rule` takes a predicate definition, the rule's _head_ and the body of the rule in the form
;; of a function. It returns a [status](status.md) containing the updated predicate definition or an explanation why
;; adding it wasn't possible.

;; Given a free variable as a first argument and an empty definition, the rule is added.
(fact
 (->s (pd-store-rule {} `(my-pred ~(atom nil) ~(atom nil) 7) (constantly 42))
      (ok get {})
      (ok apply [])) => {:ok 42})

;; If instead of a free variable, we provide anything else as a first argument in the head (on an empty definition),
;; we get an error.
(fact
 (let [x (atom nil)]
   (pd-store-rule {} `(my-pred :foo ~x 7) (constantly 42)) =>
   {:err `(specific-rule-without-base (my-pred :foo ~x 7))}))

;; One exception to this rule are predicates whose names end with `?`. These are called _partial predicates_ and
;; are not expected to provide a solution on every call.
(fact 
 (let [x (atom nil)]
   (->s (pd-store-rule {} `(my-partial-pred? :foo ~x 7) (constantly 42))
        (ok get {:keyword ":foo"})
        (ok apply []))) => {:ok 42})

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
        (pd-store-rule `(my-pred (foo ~x y0.core/& ~y) ~z 7) (constantly 43)))
   => {:err `(must-come-before (my-pred (foo ~x y0.core/& ~y) ~z 7)
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

;; #### Ambiguous Generalizations

;; Consider the following patterns (assume `x`, `y` and `z` are free variables): `(x y z)`, `(foo y z)`,
;; `(foo & z)`. How would you arrange them from the most generic to the most specific?

;; The answer is that there is not good way to do this. `(foo x y)` is obviously the most specific since
;; it sets the size of the list to be 3 and sets the first element to be `foo`, but it can be generalized
;; either by replacing `foo` with a free variable (`x`, in the first pattern) _or_ by making it variadic,
;; replacing the two variables following `foo` with a tail matching any number of elements (the third
;; pattern). The first and third patterns, however, have no order between them.

;; Therefore, if a predicate has a rule for both `(x y z)` and `(foo & z)`, and a goal for this predicate
;; contains, e.g., `(foo 1 2)`, it is impossible to know which of the two rules needs to be invoked.

;; For this reason, we make the two types: variadic forms and fixed-size lists, mutually exclusive.

;; We do so by adding markers to the predicate definition. When adding a rule for a fixed-sized list
;; without a form identifier, the key `:fixed-size-list` is added to the predicate definition, with
;; the pattern as value.
(fact
 (let [x (atom nil)
       y (atom nil)
       z (atom nil)
       w (atom nil)]
   (->s (ok {})
        (pd-store-rule `(my-pred ~x ~y 7) (constantly 42))
        (pd-store-rule `(my-pred (~x ~y ~z) ~w 7) (constantly 43))
        (ok get :fixed-size-list)) => {:ok `(~x ~y ~z)}))

;; Similarly, for a fixed-size _vector_, `:fixed-size-vec` is added.
(fact
 (let [x (atom nil)
       y (atom nil)
       z (atom nil)
       w (atom nil)]
   (->s (ok {})
        (pd-store-rule `(my-pred ~x ~y 7) (constantly 42))
        (pd-store-rule `(my-pred [~x ~y ~z] ~w 7) (constantly 43))
        (ok get :fixed-size-vec)) => {:ok [x y z]}))

;; To capture the other side, a variadic form adds the key `:variadic-form-list` for a list-based
;; form, or `:variadic-form-vec` for a vector-based form.
(fact
 (let [x (atom nil)
       y (atom nil)
       z (atom nil)]
   (->s (ok {})
        (pd-store-rule `(my-pred ~x ~y 7) (constantly 42))
        (pd-store-rule `(my-pred (foo ~x y0.core/& ~y) ~z 7) (constantly 43))
        (ok get :variadic-form-list)) => {:ok `(foo ~x y0.core/& ~y)})
 (let [x (atom nil)
       y (atom nil)
       z (atom nil)]
   (->s (ok {})
        (pd-store-rule `(my-pred ~x ~y 7) (constantly 42))
        (pd-store-rule `(my-pred [foo ~x y0.core/& ~y] ~z 7) (constantly 43))
        (ok get :variadic-form-vec)) => {:ok `(foo ~x y0.core/& ~y)}))

;; These markers are used to make sure variadic forms and fixed-size list patterns are not used in the
;; same predicate definition.
(fact
 (let [x (atom nil)
       y (atom nil)
       z (atom nil)
       w (atom nil)]
   (->s (ok {})
        (pd-store-rule `(my-pred ~x ~y 7) (constantly 42))
        (pd-store-rule `(my-pred (foo ~x y0.core/& ~y) ~z 7) (constantly 43))
        (pd-store-rule `(my-pred (~x ~y ~z) ~w 7) (constantly 44)))
   => {:err ["A rule with the pattern" `(~x ~y ~z)
             "cannot coexist with a rule with the pattern" `(foo ~x y0.core/& ~y)
             "within the same predicate due to ambiguous generalizations"]}))

;; TODO: Check the other way + for vectors.

;; #### Predicate Rules Retreival

;; Retreival of rules is done based on a goal. As a general rule, the most specific rule that matches
;; the goal is retreived.

;; The function `pd-match` takes a predicate definition and a goal and returns a rule for which the
;; head matches the goal.
(fact
 (->s (pd-store-rule {} `(my-pred ~(atom nil) ~(atom nil) 7) (constantly 42))
      (ok pd-match `(my-pred (foo 1) (bar 2) ~(atom nil)))
      (ok apply [])) => {:ok 42})

;; In case more than one definitions exist, the most specific is taken.
(fact
 (->s (ok {})
      (pd-store-rule `(my-pred ~(atom nil) ~(atom nil) 7) (constantly 42))
      (pd-store-rule `(my-pred (foo ~(atom nil)) ~(atom nil) 6) (constantly 43))
      (ok pd-match `(my-pred (foo 1) (bar 2) ~(atom nil)))
      (ok apply [])) => {:ok 43})

;; In case of a partial predicate, where no default case exists, if none of the rules match the goel,
;; `nil` is returned.
(fact
 (->s (ok {})
      (pd-store-rule `(my-partial-pred? (foo ~(atom nil)) ~(atom nil) 6) (constantly 43))
      (pd-store-rule `(my-partial-pred? (bar ~(atom nil)) ~(atom nil) 6) (constantly 44))
      (ok pd-match `(my-partial-pred? (baz 1) :quux ~(atom nil)))) => {:ok nil})

;; ### The Predicate Store

;; The predicate store is a map mapping from predicate keys to predicate definitions. The function
;; `store-rule` takes a predicate store, a rule head and a body (function) and returns a
;; [status](status.md). In case of success, the updated predicate store is returned.
(fact
 (->s (ok {})
      (store-rule `(my-pred ~(atom nil) ~(atom nil) 7) (constantly 42))
      (ok get {:name "y0.predstore-test/my-pred" :arity 3})
      (ok get {})
      (ok apply [])) => {:ok 42})

;; Retreival from the predicate store is done using the function `match-rule`, which takes a predicate
;; store and a goal and returns a status with the best match's body.
(fact
 (->s (ok {})
      (store-rule `(my-pred ~(atom nil) ~(atom nil) 7) (constantly 42))
      (store-rule `(my-pred (foo ~(atom nil)) ~(atom nil) 6) (constantly 43))
      (match-rule `(my-pred (foo 1) (bar 2) ~(atom nil)))
      (ok apply [])) => {:ok 43})

;; If the goal is for a predicate that does not exist, an `:err` is returned.
(fact
 (match-rule {} `(my-pred (foo 1) (bar 2) ~(atom nil))) => {:err `(undefined-predicate my-pred 3)})
