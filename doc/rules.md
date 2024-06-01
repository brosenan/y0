  * [Variable Bindings](#variable-bindings)
  * [Rule Parsing](#rule-parsing)
    * [Trivial Rules](#trivial-rules)
```clojure
(ns y0.rules-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.rules :refer [new-vars add-rule satisfy-goal]]
            [y0.core :refer [all <-]]
            [y0.status :refer [->s ok let-s]]
            [y0.predstore :refer [match-rule pred-key]]))

```
This module is responsible for rules and their interpretation.

## Variable Bindings

The term _variable binding_ refers to the matching between a symbol representing a variable
and the underlying variable (an `atom`). Variable bindings are represented programatically as
Clojure maps, with the symbols being keys and the atoms being the values.

In $y_0$, new variable bindings are introduced using vectors of symbols. Each symbol is
assigned a fresh variable (`(atom nil)`).

The function `new-vars` takes a binding and a vector of symbols and returns the binding updated
with the new fresh variables.
```clojure
(fact
 (let [var-binding (new-vars {} '[foo bar baz])]
   (get var-binding 'foo) => #(instance? clojure.lang.Atom %)
   @(get var-binding 'foo) => nil?
   (get var-binding 'bar) => #(instance? clojure.lang.Atom %)
   @(get var-binding 'bar) => nil?
   (get var-binding 'baz) => #(instance? clojure.lang.Atom %)
   @(get var-binding 'baz) => nil?))

```
`new-vars` override any existing variables in the bindings.
```clojure
(fact
 (let [var-binding (new-vars {'foo (atom 3)} '[foo])] 
   @(get var-binding 'foo) => nil?))

```
## Rule Parsing

The function `add-rule` takes a predstore and an s-expression which represents a logic rule
and returns a status containing the predstore, with the rule added.

### Trivial Rules

A trivial rule has the form: `(all [bindings...] head)`. Its means that for all possible goals
that replace the symbols in `bindings...` in `head` are satisfied.

To make this more clear, let us work through an example. Let us define predicate `amount`,
which matches an "amount" to a given number. `0` is matched with `:zero`, `1` is matched with
`:one` and anything else is matched with `:many`.

We define this predicate through three rules, defined here `amount0`, `amount1` and
`amount-base`.
```clojure
(def amount0 `(all [] (amount 0 :zero)))
(def amount1 `(all [] (amount 1 :one)))
(def amount-base `(all [x] (amount x :many)))

```
Note that in the first two rules, the list of bindings is empty, because the rule matches a
concrete value. The third matches any value, and so a binding of the symbol `x` is used.

Now we use `add-rule` three times to create a predstore consisting of this predicate. Note that
We add the base-rule first to comply with the [rules for defining rules](predstore.md#predicate-definitions).
The predstore should have rules to match goals of the form `(amount x y)`.
```clojure
(fact
 (let-s [ps (->s (ok {})
                 (add-rule amount-base)
                 (add-rule amount0)
                 (add-rule amount1))
         x (ok nil atom)
         r0 (match-rule ps `(amount 0 ~x))
         r1 (match-rule ps `(amount 1 ~x))
         r2 (match-rule ps `(amount 2 ~x))]
        (do (def amount-ps ps)  ;; for future reference
            (def amount-r0 r0)
            (def amount-r1 r1)
            (def amount-r2 r2)))
 amount-r0 => fn?
 amount-r1 => fn?
 amount-r2 => fn?)

```
A rule's body is a function that takes a goal and a "why not" explanation as parameters. It tries
to _satisfy_ the goal by finding an assignment for its variables. It returns a status. `{:ok nil}`
means that the goal was satisfied. In such a case, the free variables in the goal are bound to the
result.
```clojure
(fact
 (let [x (atom nil)
       goal `(amount 0 ~x)]
   (amount-r0 goal '(just-because)) => {:ok nil}
   @x => :zero)
 (let [x (atom nil)
       goal `(amount 2 ~x)]
   (amount-r2 goal '(just-because)) => {:ok nil}
   @x => :many))

```
However, if the given goal does not match the rule's head, an `:err` with the reason wny not is
returned.
```clojure
(fact
 (let [goal `(amount 2 :two)]
   (amount-r2 goal '(just-because)) => {:err '(just-because)}))

```
The function `satisfy-goal` takes a predstore, a goal and a why-not explanation. On success it
returns `(:ok nil)` and assigns the assigns values to free variables in the goal.
```clojure
(fact
 (let [x (atom nil)]
   (satisfy-goal amount-ps `(amount 1 ~x) '(just-because)) => {:ok nil}
   @x => :one))

```
On failure it returns the given explanation.
```clojure
(fact
 (satisfy-goal amount-ps `(amount 1 :uno) '(just-because)) => {:err '(just-because)})
```

