* [$y_0$ Rules](#$y_0$-rules)
  * [Deduction Rules](#deduction-rules)
    * [Goal Conditions](#goal-conditions)
```clojure
(ns rules)

```
# $y_0$ Rules

As seen in the [introduction](hello.md), a $y_0$ program is composed of rules.

In the introduction we have covered trivial rules, rules of the form
`(all [vars...] head)`, where `vars...` are zero or more symbols representing
free (unbound) variables and `head` is a s-expression that is a pattern that
may contain the symbols in `vars...`. To recap, here is an example for a rule:
```clojure
(all [x]
     (twice x [x x]))


```
This rule defines a predicate (`twice` with two arguments) that associates
anything with a vector containing that thing twice. For example:
```clojure
(test
 (twice 1 [1 1])
 (twice "two" ["two" "two"])
 (twice (x (y z)) [(x (y z)) (x (y z))]))

```
These examples show how `twice` duplicates different "things": numbers,
strings and arbitrary s-expressions.

While we can have a limited amount of fun with trivial rules, in order to
achieve our goal defining the semantics of languages through their ASTs,
we need to go deeper. In this case, we need to be able to go deeper in
the AST, to check properties of child nodes. trivial rules can only
match a single node. So to do more things we need to go beyond triviality,
and introduce _deduction rules_.

## Deduction Rules

Deduction rules are rules that allow to deduce something based on one or
more conditions. It has the form:
```clojure
(all [vars...] head <- conditions...)
```
Where `vars...` are zero or more symbols that are used as free variables
in the rest of the rule, `head` is a goal pattern, like in trivial rules
and `conditions...` are zero or more _conditions_. We will discuss what
they can be, below.

### Goal Conditions

The simplest type of condition is a _goal condition_, which is simply a
goal. A goal is an s-expression that matches the head of some rule,
so by using goal conditions we allow one rule to invoke other rules,
and even themselves (recursion), as we do in the following example.

Arguably, the simplest language with a recursive definition is the
[Peano numbers)(https://wiki.haskell.org/Peano_numbers). With words, one
would define them as follows:

1. `z` is a Peano number.
2. Given `n`, a Peano number, `(s x)` is also a peano number.

We can formulate this in $y_0$ as follows. First we define a base rule
that rejects everything, except things that will later be defined as
Peano numbers.
```clojure
(all [x] (peano x ! x "is not a Peano number"))

```
Now we provide a trivial rule for the first part of the definition: `z`
is a Peano number.
```clojure
(all [] (peano z))

```
And now we get to the recursive part:
```clojure
(all [n]
     (peano (s n)) <- (peano n))

```
In this deduction rule, we deduce that `(s n)` is a Peano number for
every `n` that is a Peano number itself.

Now let us test this predicate.
```clojure
(test
 (peano z)
 (peano (s z))
 (peano (s (s (s (s z)))))
 (peano (s y) ! y "is not a Peano number")
 (peano (s (s (k (s z)))) ! (k (s z)) "is not a Peano number"))

```
The first three examples are positive examples, showing us that the
Peano equivalents of 0, 1 and 4 are indeed Peano numbers. The last
two example shows that anything that isn't a Peano number is rejected.

Please note that the rejection is done at the lowest level possible.
The error message does not state that `(s y)` is not a Peano number
but rather says this about `y`. This is because `(s x)` for some `x`
_could be_ a Peano number. The thing that definitely cannot be a
Peano number is `y` and therefore the error complaints about it.

This is similar to a compiler pointing the user to the exact line
or symbol in which the compilation error originated, rather than
just saying "your program is broken for some reason."

The ability to point out exactly where in the AST the problem
originates is what makes $y_0$ special, compared with other logic
programming languages (yes, $y_0$ is a logic-programming language).

In particular, this is why $y_0$ looks for rules that best match goals
rather than giving all possible results, which is what languages like
Prolog and Datalog would do. By choosing the best match in advance,
the program is committed to the chosen rule. And if something fails
down the line, we know there is no alternative. We know the AST is
invalid.