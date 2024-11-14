* [Statements and Translation Rules](#statements-and-translation-rules)
  * [Translation Rules](#translation-rules)
    * [An Example](#an-example)
  * [Translating into Assertions](#translating-into-assertions)
  * [Variadic Statements](#variadic-statements)
  * [`with-meta`-Statements](#`with-meta`-statements)
    * [Meta-Vars-Must-Be-Ground](#meta-vars-must-be-ground)
  * [Exports and Imports](#exports-and-imports)
    * [The Export Statement](#the-export-statement)
    * [Imports](#imports)
```clojure
(ns statements
  (:require [hello :refer [classify]]
            [example-word-lang :refer [word]]
            [example-words :as words]))

```
# Statements and Translation Rules

Statements are top-level s-expressions in a $y_0$ program. In the
[introduction](hello.md) we have encountered two types of statements:
A _rule_, using the keyword `all`, and a _assert block_, using the
keyword `assert`.

However, $y_0$ is extensible with regard to statements, allowing
modules to define new types of statements, by giving them meaning.

The way to give meaning to a statement is to define how it
_translates_ to other types of statements, and eventually to rules
and assert blocks, which already have meaning in $y_0$.

## Translation Rules

Translation rules define the meaning of statements by defining
how they translate to other statements.

A tranlation rule has the form:
`(all [vars...] head => statements...)`, where `vars...` are zero
or more symbols representing free variables, `head` is a pattern
of the statement type being defined, possibly containing symbols
from `vars...`. `statements...` represents zero or more
s-expressions that represent statements. These could be rules,
assert blocks or other statements defined through their own 
translation rules.

### An Example

To demonstrate this, we will walk through an example. The statement
`(defoo x)` for some value `x` means that `x` is foo. The statement
`(defbar x)` for some value `x` means that `x` is bar.

The predicates `foo` and `bar` succeed on things that are either
foo or bar respectively, and fail for all others.

We begin by defining the base cases for `foo` and `bar`:
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
(assert
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
(assert
 (classify fig "A foo thingy"))

```
A translation rule may translate a single statement into multiple
statements. For example, we define the statement `defoobar` to
stand for both `defoo` and `defbar` statements.
```clojure
(all [x]
     (defoobar x) => (defoo x) (defbar x))

(defoobar fabian)

(assert
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
(assert
 (foobar fabian)
 (foobar banana ! banana "needs to be defined as both foo and bar"))

```
## Translating into Assertions

Translation rules can translate a statement to any other type
of statement, including `assert` blocks. This can be used to
add verification of statements.

Imagine we wish to extend our example with a third statement
type, `defquux`, which, like `defoo` and `defbar` will define
a caes for a predicate (`quux`, in this case). However,
for this new statement type we insist that whatever is defined
using `defquux` is _not_ defined as either foo or bar.

As usual, we start with a base case for `quux`.
```clojure
(all [x]
     (quux x ! x "is not quux"))

```
Then we define our translation rule. It translate each
`defquux` statement into two statements: a solution for `quux`
and an assertion.
```clojure
(all [x]
     (defquux x) =>
     (all [] (quux x))
     (assert
      (foo x ! x "is not foo" "in" (defquux x))
      (bar x ! x "is not bar" "in" (defquux x))))

```
Please note that we had to mention which explanation we expect
from `foo` and `bar`, since we want to make sure they fail for
the right reason.

Now we try to define a unique value as quux (and succeed), and
values that are already either foo or bar and fail. We use
`given` conditions with only a statement (and zero conditions)
in order to catch errors returned by attempts to introduce
statements.
```clojure
(assert
 (given (defquux quill))
 (given (defquux fig)
        ! "Expected failure for goal" (foo fig) "but it succeeded")
 (given (defquux banana)
        ! "Expected failure for goal" (bar banana) "but it succeeded"))

```
## Variadic Statements

Statements can be variadic, i.e., have a variable number of elements. This
is important for defining things that have "bodies", such as `defn` in
Clojure, which has a name, a parameter list and a body of zero or more
expressions.

Variadic statements are defined using the `&` operator to distinguish
between the elements that must exist and the _tail_, a variable bound to the
list of other elements.

In the following example, we define the `defbaz` statement, which takes two
or more elements. The first is condiered the key. The predicate `baz` that
is defined as a result maches the key to the rest of the elements, given as
a list.
```clojure
(all [x l]
     (baz x l ! x "is not a baz"))
(all [x l]
     (defbaz x & l) =>
     (all []
          (baz x l)))

(defbaz a b c d e)
(defbaz b c d e)
(assert
 (baz a (b c d e))
 (baz b (c d e)))

```
## `with-meta` Statements

Meta-Variables are variables that are bound to terms that contain the names
of variables. They are useful to define variable-like behavior in the
language we are defining using $y_0$.

One example is a poor-man's `defmacro` we are about to define. The predicate
`expand-macro` takes a term and returns that term with macros expanded. By
default, it will return the term unchanged.
```clojure
(all [term]
     (expand-macro term term))

(assert
 (expand-macro (foo 1 2 3) (foo 1 2 3)))

```
Simple. The definition `defmacro` adds a solution to this predicate.
```clojure
(all [name params expansion
      params-l]
     (defmacro name params expansion) =>
     (assert
      (to-list params params-l))
     (with-meta [$params params
                 $expansion expansion
                 $params-l params-l
                 $name name]
       (all $params
            (expand-macro ($name & $params-l) $expansion))))

```
To do this, we used a `with-meta` statement. This statement has the syntax:
`(with-meta [mvar val...] statement)`, where `mvar` is a symbol, `val` is 
a term (and there are zero or more of them), and `statement` is an
underlying statement.

A `with-meta` statement is evaluated by first assigining the `mvar`s with
their associated `val`s, then replacing them in `statement` and finally
applying `statement`.

So now we can define a macro.
```clojure
(defmacro foo [a b c] (+ a (* b c)))

(assert
 (expand-macro (foo 1 2 3) (+ 1 (* 2 3))))

```
In the example above, `$params`, `$expansion`, `$params-l` and `$name` are
the meta variables, which took their values from similarly-named variables,
without the `$` prefix. The `$` prefix, while not required, is used to
prevent name collisions with variables provided by the origin statement.

### Meta-Vars Must Be Ground

One restriction posed on `with-meta` is that the `val`s being assigned to
the meta variables must be _ground_. This means they cannot contain any
unbound variables.

For example, in the following translation rule, `unbound` will remain
unbound when used to initialize `$not-ground`.
```clojure
(all [x unbound]
     (defsomething x) =>
     (with-meta [$x x
                 $not-ground [1 2 unbound]]
       (defmacro $x $not-ground)))

```
Now, a `defsomething` statement will fail.
```clojure
(assert
 (exist [u]
        (given (defsomething 1))
        ! $not-ground "has non-ground value" [1 2 u]))

```
## Exports and Imports

Programming languages often have module systems that allow definitions to be
made in one module and to be used in other modules, while maintaining some
level of module isolation. A name given to something in one module does not
conflict with something else that shares the same name in another module,
unless one of them is imported to the other module.

In $y_0$, module isolation is achieved by giving symbols namespaces based on
the modules in which they are found. This way, symbol `foo` in module `a`
is represented as `a/foo` and in module `b` as `b/foo`, and are thus not
conflicting.

However, imagine module `a` exports `a/foo` and some other module, say module
`c` would like to import it. It is necessary for module `c` to be able to
reference `a/foo`.

$y_0$ provides two ways to support this. For
[EDN-based langauges](edn_parser.md), including $y_0$ itself, this is done
using the `ns` statement at the beginning of each source file. When
`:require`ing a specific symbol from a given module, when that symbol is
found in the importing module it will appear with the namespace of the
exporting module.

However, for other languages (e.g.,
[Instaparse-based languages](instaparser.md)), replacing the symbols in
advance is not possible, since the decision from which module some symbol
should come is a part of the language's semantics and should therefore be
defined in $y_0$.

To allow exactly that, $y_0$ provides the `export` and `import` statements,
described below.

### The Export Statement

An `export` statement has the following syntax:
`(export [imp exp...] statements...)`, where `exp` is a term that evaluates
to a symbol of the exporting module, `imp` is a new symbol that becomes a
free variable (there could be multiple `imp`/`exp` pairs) and `statements`
are one or more statements which may use both `imp` and `exp`.

Due to the nature of this topic, we cannot demonstrate everything in a single
module. Rather, we need three of them:

1. [A module](example-word-lang.md) to define a language with exports.
2. [A module](example-words.md) to use this language to define some things.
3. A module to import these things from the second module (this module).

We therefore refer the reader to continue reading
[here](example-word-lang.md).

### Imports

By their own, the words we have defined in the
[words example](example-words.md) do not exist in the namespace of this
module.
```clojure
(assert
 (word hello ! hello "is not a word"))

```
They are, however, imported here, so we can access them explicitly, using a
namespace alias.
```clojure
(assert
 (word words/hello))

```
An `import` statement can be used to instantiate `export` statements and thus
make words defined in a different module available in the current namespace.
```clojure
(assert
 (given (import example-words)
        (word hello)
        (word world)))

```
An `import` statement can be given a why-not explanation, which will be used
if the import fails (i.e., if the module name does not reference an existing
module).
```clojure
(assert
 (given (import wrong-module-name ! "Could not load my module")
        (word hello)
        (word world)
        ! "Could not load my module"))
```

