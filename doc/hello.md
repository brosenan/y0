* [Introduction to $y_0$](#introduction-to-$y_0$)
  * [Predicates](#predicates)
  * [Rule Specialization](#rule-specialization)
```clojure
(ns hello)

```
# Introduction to $y_0$

$y_0$ is a language for defining the _semantics_ of programming languages. It
allows for the definition of _rules_ which define which programs are valid,
and if a program isn't, provide explanations as to what the problem is.

To focus on _semantics_ rather than _syntax_, $y_0$ works on programs in the
form of their _Abstract Syntax Trees_, or ASTs for short. A parser is
expected to have parsed the program from its textual form into this tree.
$y_0$ is then used to analyze the tree and determine whether it is valid or
not.

$y_0$ uses [EDN](https://github.com/edn-format/edn) (s-expressions) for its
own syntax and for the data format it uses for the trees it acceps. For this
reason the languages we will discuss in this document are going to an
instance of EDN.

In this document we start, step by step, to define languages of growing
complexity as we introduce new features of $y_0$.

## Predicates

The basic abstraction in $y_0$ is called a _predicate_. A predicate defines a
relationship between things. At its simplest form, it can define the
relationship between a language and ASTs that are valid in that language.

To demonstrate this, consider the simplest language possible. The `bit`
language. This "language" has only two possible programs: `0` and `1`.

To define this language we need to define a _rule_ and two _facts_. The rule
is a base (catch-all rule), which matches anything and _rejects_ whatever it
matches.

All rules in $y_0$ are based on the keyword `all` followed by a list of
variable names used in the rule, followed by the rule's _head_. The following
rule (the catch-all rule for all non-bit ASTs) will also contain `!` followed
by the error to be presented to the user.
```clojure
(all [x]
     (bit x ! "Expected a bit, received" x))

```
This "catch all" rule will catch anything that does not match anything more
specific, and provide an error message stating it is not a bit.

In order to state that both `0` and `1` are indeed bits, we override the
catch-all rule with two _facts_, as follows:
```clojure
(fact
 (bit 0))
(fact
 (bit 1))

```
A fact can be thought of as a rule without free variables. While the variable
`x` in the catch-all rule is a free variable and can represent anything, the
value `1` in the fact `(fact (bit 1))` just represents itself. The same fact
could be written as a rule, with zero free variables, as: `(all [] (bit 1))`.
 
```clojure
 
```
Congratulations to us! We have just defined our first $y_0$ language by
defining a predicate that accepts it. OK, it ain't fancy or anything, but
it's a language nevertheless. But what can we do with?

For one, we can test it. We first want to test that both `0` and `1` are
accepted. We will do so in the following `test` block.
```clojure
(assert
 (bit 0)
 (bit 1))

```
What we have here is an `assert` block. It starts with the keyword `assert`
and continues with some number (two in this case) of
[conditions](conditions.md). The simplest conditions are
[goals](conditions.md#goal-conditions). One can think of goals as _calls to a
predicate_. They have a similar structure to the predicate's head, and in our
case, they have exactly the same values. This of-course will not be the case
as we define more complex languages.

An `assert` block runs when $y_0$ loads the source file. It then evaluates
the goals and expects success. If they fail, a message with the reason of the
failure is presented. Because this `.md` file is created from [a matching
`.y0` file](https://github.com/brosenan/y0/blob/main/y0_test/hello.y0), you
can be sure these goals succeed, telling us that both `0` and `1` are `bit`s.

We can also test for the error message we expect for non-bits.
```clojure
(assert
 (bit 2 ! "Expected a bit, received" 2))

```
Here the meaning of the `!` symbol is different: it means that an error is
_expected_.

This test succeeds because the error message we provided after the `!`
matches the one provided by the error-case rule exactly.

## Rule Specialization

As we have seen in the previous section, when defining a predicate we need to start
from the most general form, a form that _must_ take a variable as its first argument
(the _only_ argument in the above case) and work our way down to more specific rules.

The next predicate we define is a simple "classifier" for s-expressions. This predicate
takes two arguments, which means that it defines the relationship between two things:
an AST and a string describing it.

The predicate will always succeed. To make sure it does, the base case needs to succeed.
```clojure
(all [x]
     (classify x "I don't know what it is"))

```
This makes sure that even if the classifier doesn't have a specific answer for a given
input, it will still provide an answer, albeit not a useful one.
```clojure
(assert
 (classify 1 "I don't know what it is"))

```
Now we can define special cases. We start with a special case for the number `1`.
```clojure
(fact
 (classify 1 "The number one"))

```
Now, if we run the test again, we get a usefule answer.
```clojure
(assert
 (classify 1 "The number one"))

```
Next, we will define a rule for `()`, an empty list.
```clojure
(fact
 (classify () "An empty list"))

```
Lists are overloaded in langauges based on s-expressions to act as _forms_. A form
is a list where the first element, typically a symbol, determines the type of object
the list represets. In our case, the symbol at the beginning of a list defines the
_type of node_ the list represents in the AST.

In the following examples we define a `foo` node, which can take any number of
arguments.
```clojure
(all [x xs]
     (classify (foo x & xs) "A foo node with some number of arguments"))

```
Note that the `&` symbol means that everything that the variable that follows it
is matched to the _list of elements_ from this position on. This rule is therefore
a match to `foo` nodes with any number of arguments:
```clojure
(assert
 (classify (foo 1) "A foo node with some number of arguments")
 (classify (foo 1 2 3 4 5) "A foo node with some number of arguments"))

```
However, we can specialize `classify` further, to allow treatment for `foo` nodes
with a specific number of arguments.
```clojure
(all [x1]
     (classify (foo x1) "A foo node with exactly one argument"))
(all [x1 x2 x3]
     (classify (foo x1 x2) "A foo node with exactly two arguments"))
(all [x1 x2 x3]
     (classify (foo x1 x2 x3) "A foo node with exactly three arguments"))

(assert
 (classify (foo 1) "A foo node with exactly one argument")
 (classify (foo 1 2) "A foo node with exactly two arguments")
 (classify (foo 1 2 3) "A foo node with exactly three arguments")
 (classify (foo 1 2 3 4 5) "A foo node with some number of arguments"))

```
In this example we choose to treat vectors differently, and define classifications
for vectors of different sizes (and any size), regardless of the first element.
```clojure
(all [x xs]
     (classify [x & xs] "A vector with some elements"))
(fact
 (classify [] "An empty vector"))
(all [x1]
     (classify [x1] "A vector with one element"))
(all [x1 x2]
     (classify [x1 x2] "A vector with two elements"))
(all [x1 x2 x3]
     (classify [x1 x2 x3] "A vector with three elements"))
(assert
 (classify [] "An empty vector")
 (classify ["foo"] "A vector with one element")
 (classify ["foo" "bar"] "A vector with two elements")
 (classify ["foo" "bar" "baz"] "A vector with three elements")
 (classify ["foo" "bar" "baz" "quux"] "A vector with some elements"))

```
It is, however, not possible to mix. If, for example, we try defining the rule:
```clojure
(all [x1 x2 x3]
     (classify (x1 x2 x3) "A list with three elements"))
```

We will get an error. The reason is that now, a goal such as `(classify (foo 1 2) x)`
can be interpreted as either matching the rule for a `foo` node with two arguments
_or_ the rule for lists of size 3. To overcome this problem, $y_0$ imposes mutual
exclusion between the two types of patterns. This mutual exclusion is separate for
lists and vectors and therefore, as in this example, we can make different choices
for lists and vectors. In this example we chose to treat lists as forms and vectors
as tuples. However, we could have made any other choice.

For a more accurate specification of this feature see
[$y_0$'s internal documentation](predstore.md#ambiguous-generalizations).
