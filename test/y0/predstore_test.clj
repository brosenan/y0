(ns y0.predstore-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.predstore :refer [pred-key arg-key arg-key-generalizations
                                  pd-store-rule pd-match store-rule match-rule generalize-arg
                                  store-translation-rule store-statement get-rules-to-match
                                  get-statements-to-match]]
            [y0.core :refer [&]]
            [y0.status :refer [ok ->s let-s]]))

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

;; An empty list is represented as `{:list 0}`.
(fact
 (arg-key '()) => {:list 0})

;; A pattern matching a non-empty list is represented by `{:list :any}`
(fact
 (arg-key `(~(atom nil) y0.core/& ~(atom nil))) => {:list :any})

;; If the length of the list is known, the value of `:list` becomes the length.
(fact
 (arg-key `(~(atom nil) ~(atom nil) ~(atom nil))) => {:list 3})

;; The key of the first element in the list is merged with the list's key, to give a key for the completed _form_.
(fact
 (arg-key `(foo ~(atom nil) ~(atom nil))) => {:list 3 :symbol "y0.predstore-test/foo"}
 (arg-key `(foo y0.core/& ~(atom nil))) => {:list :any :symbol "y0.predstore-test/foo"}
 (arg-key `(:foo ~(atom nil))) => {:list 2 :keyword ":foo"}
 (arg-key `(42 y0.core/& ~(atom nil))) => {:list :any :value 42})

;; Vectors are similar to lists, but use the `:vec` attribute rather than `:list`.
(fact
 (arg-key []) => {:vec 0}
 (arg-key [(atom nil) 'y0.core/& (atom nil)]) => {:vec :any}
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

;; Lists and vectors with a known size are generalized to `:any`.
(fact
 (generalize-arg {:list 3
                  :vec 4}) => [{:list :any
                                :vec 4}
                               {:list 3
                                :vec :any}])

;; Lists and vectors that are already `:any` are removed, but only as long as there are no other markers in the
;; key.
(fact
 (generalize-arg {:list :any}) => [{}]
 (generalize-arg {:vec :any}) => [{}]
 (generalize-arg {:list :any
                  :something :else}) => []
 (generalize-arg {:vec :any
                  :something :else}) => [])

;; The function `arg-key-generalizations` uses `generalize-arg` to create a sequence of all (transitive) generalizations
;; of a given key.

(fact
 (arg-key-generalizations {:list 3 :symbol "foo"}) => [{:list 3 :symbol "foo"}
                                                       {:list 3}
                                                       {:list :any :symbol "foo"}
                                                       {:list :any}
                                                       {}]
 (arg-key-generalizations {:vec 3 :symbol "foo"}) => [{:vec 3 :symbol "foo"}
                                                      {:vec 3}
                                                      {:vec :any :symbol "foo"}
                                                      {:vec :any}
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
   {:err ["A specific rule for" `(my-pred :foo ~x 7) 
          "is defined without first defining a base rule for the predicate with a free variable as its first argument"]}))

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
   => {:err ["Rule" `(my-pred (foo ~x y0.core/& ~y) ~z 7) "must be defined before rule"
             `(my-pred (foo ~x ~y) ~z 7) "because it is more generic"]}))

;; And of course, if two rules have the exact same first-arg pattern, this is a conflict.
(fact
 (let [x (atom nil)
       y (atom nil)
       z (atom nil)]
   (->s (ok {})
        (pd-store-rule `(my-pred ~x ~z 7) (constantly 42))
        (pd-store-rule `(my-pred (foo ~x ~y) ~z 7) (constantly 44))
        (pd-store-rule `(my-pred (foo ~x ~z) ~y 8) (constantly 43)))
   => {:err ["The rule for" `(my-pred (foo ~x ~z) ~y 8)
             "conflicts with a previous rule defining"
             `(my-pred (foo ~x ~y) ~z 7)]}))

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
 (match-rule {} `(my-pred (foo 1) (bar 2) ~(atom nil))) =>
 {:err ["Undefined predicate" `my-pred "with arity" 3]})

;; ## Storage and Retreival of Statements and Translation Rules

;; Translation rules translate applicable statements to zero or more other statements.
;; Translation rules are stored in the predstore based on their heads. We use `arg-key`
;; on the head to determine `k` in the key `{:translations k}`. This key is mapped to
;; a _set of functions_, each representing a translation rule on statements that match
;; the key.

;; The function `store-translation-rule` takes a predstore, a head (s-expression) and
;; a function which represents the body and stores it under said key. It returns a
;; status containing the updated predstore.
(fact
 (let-s [res (->s {}
                  (store-translation-rule `(defoo (atom nil)) (constantly 42))
                  (store-translation-rule `(defoo (atom nil)) (constantly 43))
                  (ok get {:translations {:list 2 :symbol "y0.predstore-test/defoo"}}))]
        (do res => set?
            (->> res
                 (map #(apply % []))
                 (into #{})) => #{42 43})))

;; Statements are also stored in a similar manner. `store-statement` takes a predstore
;; and a statement (s-expression) and adds it to a key. The key is of the form:
;; `{:statements k}`, where `k` is the result of `arg-key` applied to the statement.
;; The key maps to a set of statements that share the same key.
(fact
 (let-s [res (->s {}
                  (store-statement `(defoo 1))
                  (store-statement `(defoo 2))
                  (ok get {:statements {:list 2 :symbol "y0.predstore-test/defoo"}}))]
        res => #{`(defoo 1) `(defoo 2)}))

;; `get-rules-to-match` takes a predstore and a statement and returns the rules that
;; (may) match it. Matching is done by the key, so it is up to the rule function to
;; really determine if it really matches the statement.
(fact
 (let-s [res (->s {}
                  (store-translation-rule `(defoo (atom nil)) (constantly 42))
                  (store-translation-rule `(defoo (atom nil)) (constantly 43))
                  (ok get-rules-to-match `(defoo 1)))]
        (do res => set?
            (->> res
                 (map #(apply % []))
                 (into #{})) => #{42 43})))

;; If the statement doesn't match any rules, an empty set is returned.
(fact
 (let-s [res (->s {}
                  (store-translation-rule `(defoo (atom nil)) (constantly 42))
                  (store-translation-rule `(defoo (atom nil)) (constantly 43))
                  (ok get-rules-to-match `(defbar 1)))]
        res => #{}))

;; Similarly, `get-statements-to-match` take a head of a translation rule and returns
;; all matching statements (again, matching by the key).
(fact
 (let-s [res (->s {}
                  (store-statement `(defoo 1))
                  (store-statement `(defoo 2))
                  (ok get-statements-to-match `(defoo (atom nil))))]
        res => #{`(defoo 1) `(defoo 2)}))

;; Or an empty set if no such statements were found.
(fact
 (let-s [res (->s {}
                  (store-statement `(defoo 1))
                  (store-statement `(defoo 2))
                  (ok get-statements-to-match `(defbar (atom nil))))]
        res => #{}))
