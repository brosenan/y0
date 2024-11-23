(ns y0.rules-test
  (:require
   [midje.sweet :refer [=> fact]]
   [y0.core :refer [! all <- given] :as y0]
   [y0.explanation :refer [explanation-to-str]]
   [y0.predstore :refer [get-statements-to-match match-rule]]
   [y0.rules :refer [add-rule apply-normal-statement new-vars satisfy-goal]]
   [y0.status :refer [->s let-s ok]]
   [y0.unify :refer [reify-term]]))

;; This module is responsible for rules and their interpretation.

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

;; ## Rule Parsing

;; The function `add-rule` takes a predstore, an s-expression which represents a logic rule and
;; an initial varmap and returns a status containing the predstore, with the rule added.

;; ### Trivial Rules

;; A trivial rule has the form: `(all [bindings...] head)`. Its means that for all possible goals
;; that replace the symbols in `bindings...` in `head` are satisfied.

;; To make this more clear, let us work through an example. Let us define predicate `amount`,
;; which matches an "amount" to a given number. `0` is matched with `:zero`, `1` is matched with
;; `:one` and anything else is matched with `:many`.

;; We define this predicate through three rules, defined here `amount0`, `amount1` and
;; `amount-base`.
(def amount0 `(all [] (amount 0 :zero)))
(def amount1 `(all [] (amount 1 :one)))
(def amount-base `(all [x] (amount x :many)))

;; Note that in the first two rules, the list of bindings is empty, because the rule matches a
;; concrete value. The third matches any value, and so a binding of the symbol `x` is used.

;; Now we use `add-rule` three times to create a predstore consisting of this predicate. Note that
;; We add the base-rule first to comply with the [rules for defining rules](predstore.md#predicate-definitions).
;; The predstore should have rules to match goals of the form `(amount x y)`.
(fact
 (let-s [ps (->s (ok {})
                 (add-rule amount-base {})
                 (add-rule amount0 {})
                 (add-rule amount1 {}))
         x (ok nil atom)
         r0 (match-rule ps `(amount 0 ~x))
         r1 (match-rule ps `(amount 1 ~x))
         r2 (match-rule ps `(amount 2 ~x))]
        (do (def amount-ps ps)  ;; for future reference
            (def amount-r0 r0)
            (def amount-r1 r1)
            (def amount-r2 r2)
            (ok nil))) => {:ok nil}
 amount-r0 => fn?
 amount-r1 => fn?
 amount-r2 => fn?)

;; A rule's body is a function that takes a goal, a "why not" explanation and the predstore as parameters.
;; It tries to _satisfy_ the goal by finding an assignment for its variables. It returns a status.
;; `{:ok nil}` means that the goal was satisfied. In such a case, the free variables in the goal are bound
;; to the result.
(fact
 (let [x (atom nil)
       goal `(amount 0 ~x)]
   (amount-r0 goal ["just-because"] amount-ps) => {:ok nil}
   @x => :zero)
 (let [x (atom nil)
       goal `(amount 2 ~x)]
   (amount-r2 goal ["just-because"] amount-ps) => {:ok nil}
   @x => :many))

;; However, if the given goal does not match the rule's head, an `:err` with the reason wny not is
;; returned.
(fact
 (let [goal `(amount 2 :two)]
   (amount-r2 goal '(just-because) amount-ps) => {:err '(just-because)}))

;; Calls to rules are independent. A rule can be called with different values each time independently.
(fact
 (let [x1 (atom nil)
       x2 (atom nil)]
   (amount-r2 `(amount 2 ~x1) ["because-of-1"] amount-ps) => {:ok nil}
   @x1 => :many
   (amount-r2 `(amount 3 ~x2) ["because-of-2"] amount-ps) => {:ok nil}
   @x2 => :many))

;; The function `satisfy-goal` takes a predstore, a goal and a why-not explanation. On success it
;; returns `(:ok nil)` and assigns the assigns values to free variables in the goal.
(fact
 (let [x (atom nil)]
   (satisfy-goal amount-ps `(amount 1 ~x) ["just-because"]) => {:ok nil}
   @x => :one))

;; On failure it returns the given explanation.
(fact
 (satisfy-goal amount-ps `(amount 1 :uno) ["just-because"]) => {:err ["just-because"]})

;; ### Providing Why-Not Explanations

;; If the goal sequence contains a `!` symbol, the goal to be evaluated is everything to the left of
;; the `!`.
(fact
 (let [x (atom nil)]
   (satisfy-goal amount-ps `(amount 1 ~x ! "some explanation") ["just-because"]) => {:ok nil}
   @x => :one))

;; The terms after the `!` override the provided why-not explanation in case of a failure.
(fact
 (satisfy-goal amount-ps `(amount 1 :uno ! "the" "correct" "explanation") ["with some context"]) =>
 {:err ["the" "correct" "explanation" "with some context"]})

;; A rule can provide its own why not explanation. Rules can be provided to always fail, providing
;; an explanation. This is useful, for example, when rules are provided to cover all "valid" special
;; cases, but we want to make the default fail with a proper explanation, e.g., x is not a valid y.

;; Creating a failing rule is done by placing a `!` in its head. If there is any context provided,
;; it is appended to the explanation.

;; To demonstrate this, we extend the `amount` example to have a failing case for `-1`.
(fact
 (let [x (atom nil)]
   (->s (ok amount-ps)
        (add-rule `(all [x] (amount -1 x ! "Cannot have negative amounts")) {})
        (satisfy-goal `(amount -1 ~x) ["with some context"]))) =>
 {:err ["Cannot have negative amounts" "with some context"]})

;; The provided explanation may contain variables shared with the head. These make the explanation
;; refer to the arguments that were given. For example, the following rule takes a 1-place vector
;; and explains that it cannot provide an amount for a vector containing that value.
(fact
 (let [y (atom nil)
       status (->s (ok amount-ps)
                   (add-rule `(all [x xs y] (amount [x y0.core/& xs] y ! "Cannot provide an amount for a vector containing" x)) {})
                   (satisfy-goal `(amount [6] ~y) ["with some context"]))]
   (explanation-to-str (:err status)) =>
   "Cannot provide an amount for a vector containing 6 with some context"))

;; ## Handling Statements

;; A full account of the handling of statements and translation rules is given
;; [here](statements.md). Here, however, we describe behavior that cannot be
;; properly tested internally, from within $y_0$.

;; ### Reporting Unhandled Statements

;; $y_0$ statements can be of any shape and form (so long that they are
;; representable as EDN s-expressions). However, they only have meaning if they
;; are either of one of the two statements types that have built-in behavior
;; (namely, `all` or `assert`), or if there is a translation rule that operates
;; on them.

;; Because other statements have no effect, we make them illegal. This makes
;; sure that every statement is accounted for and avoids situations where a
;; translation rule that should have acted on a family of statements actually
;; fails to do so because it got the pattern wrong.

;; The function `apply-normal-statement` is responsible for handling "normal"
;; statements, i.e., statements that are not explicitly understood by $y_0$. It
;; takes a predstore, the statement and a map of variables and returns the
;; updated predstore, after applying all relevant translation rules and thus
;; adding all resulting statements.

;; `apply-normal-statement` succeeds if the statement matches at least on
;; translation rule.
(fact
 (let [x (atom nil)
       status (->s (ok {})
                   (add-rule `(all [x] (my-stmt x) y0/=> (assert (= x x))) {})
                   (apply-normal-statement `(my-stmt 123) {}))]
   (:err status) => nil?))

;; However, without first introducing a translation rule, the statement is
;; pointless and therefore not allowed.
(fact
 (let [x (atom nil)
       status (->s (ok {})
                   (apply-normal-statement `(my-stmt 123) {}))]
   (:err status) =>
   ["No rules are defined to translate statement" `(my-stmt 123)
    "and therefore it does not have any meaning"]))

;; ## Tree Decoration

;; One of the benefits of having a declarative language for defining the
;; semantics of programming languages is that it can be used to retrieve
;; semantic information about a program automatically, without the need to
;; explicitly program this for every language.

;; In $y_0$, every time a rule is applied to a node in the parse tree, some
;; semantic information can be gathered. If the tree node has
;; [mutable decorations](polyglot_loader.md#optional-decoration), they will be
;; updated with information about the application of this rule.

;; ### Tracing Definitions

;; One of the primary goals of parse-tree decorations is to connect nodes to
;; their definitions. One of the benefits of $y_0$ is that it makes definitions
;; first class citizens with the use of translation rules and `given`
;; conditions.

;; However, the given a rule that applies to a node, it is still not trivial to
;; tell which of the many things that can be considered a "definition" is the
;; most useful to the user. This is because some statements are generated by the
;; language definition (using translation rules) and are therefore not useful
;; for the user, since they are not part of their source code.

;; We can overcome this problem by tracing the translation rules back to the
;; original statement, which (almost) definitely originates from the user's
;; code. Unfortunately, this is not necessarily a good solution either, because
;; in some cases translation rules are used to deconstruct large statements
;; (e.g., a class definition in an OOP language) into smaller statements. In
;; these cases tracing all the way down the translation rule chain would result
;; connecting everything that is defined in that large definition (e.g., methods
;; of the class) to that large definition rather to its components.

;; The solve this problem by first marking tree nodes of user-generated source
;; files using [decorations](polyglot_loader.md#optional-decoration). Then, we
;; track definitions of statements (including rules) through translation rules
;; and `given` definitions.

;; We define the helper function `decorated` to add empty decorations to a
;; specific node, marking it as a node in the user's source-code.
(defn- decorated [node]
  (vary-meta node #(-> %
                       (assoc :matches (atom {}))
                       (assoc :refs (atom nil)))))

;; In this subsection we discuss the details of this tracking, and in the
;; following subsections we discuss how these tracked definitions are being
;; used.

;; #### Definition Tracking through Translation Rules

;; When a statement is derived from a decorated statement, the source statement
;; is marked as the generated statement's `:def` meta property.
(fact
 (let-s [ps (->s (ok {})
                 (add-rule `(all [x] (foo x) y0/=> (bar x)) {})
                 (add-rule `(all [x] (bar x) y0/=>) {})
                 (apply-normal-statement (decorated `(foo 42)) {}))
         stmts (ok ps get-statements-to-match `(bar ~(atom nil)))]
        (-> stmts first meta :def)) => `(foo 42))

;; If the source statement is not decorated but its first argument is, the first
;; argument is taken as the `:def`.
(fact
 (let-s [ps (->s (ok {})
                 (add-rule `(all [x] (foo x) y0/=> (bar x)) {})
                 (add-rule `(all [x] (bar x) y0/=>) {})
                 (apply-normal-statement `(foo ~(decorated `baz)) {}))
         stmts (ok ps get-statements-to-match `(bar ~(atom nil)))]
        (-> stmts first meta :def)) => `baz)

;; This should also work when the first argument is a variable.
(fact
 (let-s [ps (->s (ok {})
                 (add-rule `(all [x] (foo x) y0/=> (bar x)) {})
                 (add-rule `(all [x] (bar x) y0/=>) {})
                 (apply-normal-statement `(foo ~(atom (decorated `baz))) {}))
         stmts (ok ps get-statements-to-match `(bar ~(atom nil)))]
        (-> stmts first meta :def)) => `baz)

;; If the first argument is also not decorated, the `:origin` of the source is
;; taken.
(fact
 (let-s [ps (->s (ok {})
                 (add-rule `(all [x] (foo x) y0/=> (bar x)) {})
                 (add-rule `(all [x] (bar x) y0/=>) {})
                 (apply-normal-statement (with-meta `(foo 42) {:origin :quux}) {}))
         stmts (ok ps get-statements-to-match `(bar ~(atom nil)))]
        (-> stmts first meta :def)) => :quux)

;; #### Tracking Definitions for `given` Conditions

;; If a statement is created within a `given` condition in a deduction rule, the
;; rule's _subject_ (i.e., the predicate's first argument) is considered the
;; definition.

;; Testing this functionality is a bit tricky. We will define a predicate
;; `check` with a custom function, which will bind to its argument the internal
;; scope (`ps`) it is handed. Then we will define the following rule:

;; ```clojure
;; (all [x]
;;      (foo x) <-
;;      (given (bar x)
;;             (check ps')))
;; ```

;; where `ps'` is an extrernally-provided free variable. With that, we will
;; evaluate predicate `(foo 42)`, and inspect the result we receive. The
;; returned predstore should contain statement `(bar 42)`, and its `:def` should
;; point to 42.
(fact
 (let-s [ps' (ok nil atom)
         ps (->s (ok {{:name "y0.rules-test/check"
                       :arity 1} {{} (fn [goal _why-not ps]
                                       (let [[_check ps'] goal]
                                         (reset! ps' ps)
                                         (ok nil)))}})
                 (add-rule `(all [x] (bar x) y0/=>) {})
                 (add-rule `(all [x]
                                 (foo x) <-
                                 (given (bar x)
                                        (check ps'))) {`ps' ps'}))
         foo-rule (match-rule ps `(foo 42))
         _ (foo-rule `(foo 42) "just because" ps)]
        (-> @ps'
            (get-statements-to-match `(bar ~(atom nil)))
            first
            meta
            :def)) => 42)

;; #### Propagating `:def` to Rule Bodies

;; Finally, in order for rules to have access to their definition, the `:def`
;; meta property needs to propagate all the way to the rule's body (the rule
;; function).

;; To demonstrate this we create a translation rule that results in a deduction
;; rule. We then add a statement that triggers this rule and extract the rule to
;; see that the rule has a `:def` meta property that points to the statement.
(fact
 (let-s [ps (->s (ok {})
                 (add-rule `(all [x] (foo x) y0/=> (all [] (bar x))) {})
                 (add-rule `(all [x] (bar x)) {})
                 (apply-normal-statement (decorated `(foo 42)) {}))
         rule (match-rule ps `(bar 42))]
        (-> rule meta :def)) => `(foo 42))

;; ### Updating Decorations

;; To demonstrate this we start by defining a simple language, where _variables_
;; can be defined with _types_, and then used in _expressions_.
(let-s [ps (->s (ok {})
                (add-rule `(all [x t] (expr x t ! x "is not an expression")) {})
                (add-rule `(all [v t] (defvar v t) y0/=> (all [] (expr v t))) {}))]
       (def varlang-ps ps))

;; Now let us introduce a "program" that defines variable `foo` of type `bar`.
;; We will then create a node referencing `foo` and add an empty `:matches`
;; decoration to it. Then we will call `satisfy-goal` with predicate `expr` and a
;; variable for the type. Finally, we will examine the decoration value.
(fact
 (let-s [ps (apply-normal-statement varlang-ps `(defvar foo bar) {})
         node (ok `foo with-meta {:matches (atom {})})
         t (ok (atom nil))
         _ (satisfy-goal ps `(expr ~node ~t) ["Something went wrong"])]
        (-> node meta :matches deref reify-term) =>
        {`expr {:args [`bar]
                :def `(defvar foo bar)}}))

;; Given these decorations, one cal learn the following things about the node:
;; 1. It is an expression (`expr`).
;; 2. It is of type `bar`.
;; 3. It was defined in the node `(defvar foo bar)`, which in term has pointers
;;    to the code location in which it was created.

;; ### Tracing References

;; In the previous example we saw how the `:def` attribute is populated to link
;; to the definition. It is also desired, however, to collect pointers in the
;; opposite direction, i.e., from the definition to its references.

;; To do this, we give the definition a mutable `:refs` decoration, containing
;; an empty sequence (`nil`). This set will then be populated with nodes that
;; are referencing this definition.
(fact
 (let-s [definition (ok `(defvar foo bar) with-meta {:refs (atom nil)})
         ps (apply-normal-statement varlang-ps definition {})
         t (ok (atom nil))
         _ (satisfy-goal ps `(expr foo ~t) ["Something went wrong"])]
        (-> definition meta :refs deref reify-term) => [`foo]))

;; However, sometimes the definition is synthetic, created by a translation rule
;; based on elements from the source code. In such cases, it is common that the
;; first argument of the definition (the statement's subject) is a node in the
;; user's code parse-tree. In such cases we wish to make the reference from
;; there.

;; So, if the definition itself has no `:refs` decoration but the first argument
;; does, we add the node to the first argument's `:refs`.
(fact
 (let-s [first-arg (ok `foo with-meta {:refs (atom nil)})
         definition (ok `(defvar ~first-arg bar))
         ps (apply-normal-statement varlang-ps definition {})
         t (ok (atom nil))
         _ (satisfy-goal ps `(expr foo ~t) ["Something went wrong"])]
        (-> first-arg meta :refs deref reify-term) => [`foo]))