* [Statements and Translation Rules](#statements-and-translation-rules)
  * [Translation Rules](#translation-rules)
    * [An Example](#an-example)
```clojure
(ns statements
  (:require [hello :refer [classify]]))

```
# Statements and Translation Rules

Statements are top-level s-expressions in a $y_0$ program. In the
[introduction](hello.md) we have encountered two types of statements:
A _rule_, using the keyword `all`, and a _test block_, using the
keyword `test`.

However, $y_0$ is extensible with regard to statements, allowing
modules to define new types of statements, by giving them meaning.

The way to give meaning to a statement is to define how it
_translates_ to other types of statements, and eventually to rules
and test blocks, which already have meaning in $y_0$.

## Translation Rules

Translation rules define the meaning of statements by defining
how they translate to other statements.

A tranlation rule has the form:
`(all [vars...] head => statements...)`, where `vars...` are zero
or more symbols representing free variables, `head` is a pattern
of the statement type being defined, possibly containing symbols
from `vars...`. `statements...` represents zero or more
s-expressions that represent statements. These could be rules,
test blocks or other statements defined through their own 
translation rules.

### An Example

To demonstrate this, we will walk through an example. The statement
`(defoo x)` for some value `x` means that `x` is foo. The statement
`(defbar x)` for some value `x` means that `x` is bar.

The predicates `foo` and `bar` succeed on things that are either
foo or bar respectively, and fail for all others.

We begin by defining the base cases for `is-foo` and `is-bar`:
```clojure
(all [x]
     (foo x ! x "is not foo"))
(all [x]
     (bar x ! x "is not bar"))

```
Now, to give meaning to `defoo` and `defbar`, we define a translation
rule for each of them.
```clojure
(all [x]
     (defoo x) => (all [] (foo x)))
(all [x]
     (defbar x) => (all [] (bar x)))

```
These rules translate each statement into a trivial rule that defines
the value provided in the statement as either foo or bar.

Now we can use the newly-created statements...
```clojure
(defoo fig)
(defbar banana)

```
And expect to see their effect.
```clojure
(test
 (foo fig)
 (bar banana)
 (foo banana ! banana "is not foo")
 (bar fig ! fig "is not bar"))

```
Rules apply to statements made after _and before_ they were introduced.
In the following example we define a rule that will take existing
`defoo` statements and will create a
[classification](hello.md#rule-specialization) of the defined object.
```clojure
(all [x]
     (defoo x) => (all [] (classify x "A foo thingy")))

```
This retrospectively appies to `fig`, which was previously defined as
foo.
```clojure
(test
 (classify fig "A foo thingy"))

```
A translation rule may translate a single statement into multiple
statements. For example, we define the statement `defoobar` to
stand for both `defoo` and `defbar` statements.
```clojure
(all [x]
     (defoobar x) => (defoo x) (defbar x))

(defoobar fabian)

(test
 (foo fabian)
 (bar fabian))

```
Translation rules can translate statements into other translation
rules. This is useful for making predicates depend on two or more
statements.

For example, we can define the predicate `foobar`, which requires
the something is defined both as foo and as bar. We could have
used a [deduction rule](conditions.md#deduction-rules) with
conditions on `foo` and on `bar`, but if we want to depend on the
definitions directly, we can do this as follows:
```clojure
(all [x] (foobar x ! x "needs to be defined as both foo and bar"))
(all [x] (defoo x) =>
     (all [] (defbar x) =>
          (all [] (foobar x))))

```
Now, anything that is defined as both `foo` and `bar` should be
`foobar`.
```clojure
(test
 (foobar fabian)
 (foobar banana ! banana "needs to be defined as both foo and bar"))
```

