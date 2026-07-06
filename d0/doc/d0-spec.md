# `D0` A Language for Defining Dynamic Semantics

This is a language spec for the `D0` language, a language for defining the
_dynamic semantics_ of programming languages.

Language: `d0`

## Traits

The basic unit of declaration in `D0` is a _trait_. Traits in `D0` take their
idea from their namesakes in Rust (and type-classes in Haskell), but apply
strictly to parse-trees. A `D0` trait corresponds to a $y_0$ predicate, but
while $y_0$ is about determining whether a given (sub)tree is accepted by a
given predicate, `D0` is about giving these predicate meaning in the form of
methods that need to be implemented for this tree. This means that if the $y_0$
definition of a language accepts a certain tree as matching predicate $x$, `D0`
may define trait $x$, with methods that need to be implemented for every tree
that matches predicate $x$. This way, each valid tree is guaranteed to provide a
valid implementation of these methods, and hence have a way to execute the
underlying program.

Traits are defined using the `deftrait` definition. The following is a minimal
definition of a trait.

```clojure
(ns example)

(deftrait simple-trait [])
```
```status
Success
```

A trait name must be a symbol.

```clojure
(ns example)

(deftrait "bad-name" [])
```
```status
ERROR: A trait name must be a symbol, but bad-name is given in (deftrait "bad-name" [])
```

A trait name must only use lowercase letters, digits and dashes.

```clojure
(ns example)

(deftrait Bad-name [])
```
```status
ERROR: Invalid trait name Bad-name . A trait name should only use lower-case letters, digits and dashes in (deftrait Bad-name [])
```

The argument list in a trait definition must include symbols, as placeholders
for arguments of $y_0$ predicates, such as the type for an expression.

```clojure
(ns example)

(deftrait my-trait [arg1 Arg2 +arg3])
```
```status
Success
```

```clojure
(ns example)

(deftrait my-trait [arg1 42 +arg3])
```
```status
ERROR: 42 is not a valid trait parameter. Symbol expected in (deftrait my-trait [arg1 ...])
```

The trait parameters can be followed by declarations. We will cover the possible
declarations in the following subsections, but below is an example of an invalid
one:

```clojure
(ns example)

(deftrait my-trait [arg1 Arg2 +arg3]
  (some-invalid-decl))
```
```status
ERROR: invalid trait declaration (some-invalid-decl) in (deftrait my-trait [arg1 ...] ...)
```

### Associated Type Declarations

Associated types are types that are declared by a trait and are then defined for
each instance of the trait. `D0` traits can declare these using `decltype`
declarations.

```clojure
(ns example)

(deftrait my-trait [some params]
  (decltype MyAssociatedType))
```
```status
Success
```

The name of the declared type must be a valid type name, Starting with an
upper-case letter, consisting only of upper and lower-case letters and digits.

```clojure
(ns example)

(deftrait my-trait [some params]
  (decltype badTypeName))
```
```status
ERROR: Invalid type name  badTypeName . A type name should start with an upper-case letter, followed by lower and upper case letters and digits in (deftrait my-trait [some ...] ...)
```

### Method Declarations

Traits can define methods. Methods are functions that operate on a parse-tree
node and possibly additional arguments, and is implemented differently for
different types of nodes, thus implementing the semantics of the language.

Method are declared using the `declmethod` declaration, providing the name, an
argument list and a return type.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod my-method [] Int64))
```
```status
Success
```

The return type must a valid type.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod my-method [] not-a-type))
```
```status
ERROR: not-a-type is not a declared type in (deftrait my-trait [] ...)
```

The same applies to all the parameters, if declared.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod my-method [Int64 not-a-type] Int64))
```
```status
ERROR: not-a-type is not a declared type in (deftrait my-trait [] ...)
```

### Associated Types used in Method Declarations

This is where trait definitions become interesting. In addition to primitive
types such as `Int64`, method declerations can also use associated types from
other (previously-defined) classes.

Consider for example a language with expressions. Each expression has a type.
The type `t` will be a trait-parameter of the `expr` trait. `t` is expected
to be a `type`, even though this is implicit in `D0`. The `type` trait declares
a `RunType` associated type, which stands for the `D0` equivalent (the actual
type at runtime) for `t`. This will be the return type from `expr`'s only
method: `eval`.

```clojure
(ns example)

(deftrait type []
  (decltype RunType))

(deftrait expr [t]
  (declmethod eval [] (RunType t)))
```
```status
Success
```

The argument to an associated type must be a parse-tree node. In the context of
a trait definition, the only place to find such nodes is the list of trait
parameters.

```clojure
(ns example)

(deftrait type []
  (decltype RunType))

(deftrait expr [t]
  (declmethod eval [] (RunType anything-other-than-t)))
```
```status
ERROR: anything-other-than-t does not represent a parse-tree node in this context in (deftrait expr [t] ...)
```

## Implementations

Each trait can have multiple _implementation blocks_, defining the declared
types and functions for a given parse-tree node pattern.

An implementation block is defined using the `impl` symbol. A minimal definition
looks like this:

```clojure
(ns example)

(deftrait my-trait [])

(impl [] (my-trait) :foo)
```
```status
Success
```

The trait must be a defined trait.

```clojure
(ns example)

(impl [] (my-trait) :foo)
```
```status
ERROR: (my-trait) is not a trait in (impl [] (my-trait) ...)
```

The trait pattern used in a `impl` block consists of a name of a defined trait,
followed by placeholders for each parameters. The placeholders must be variables
defined in the free-variables list provided before the trait pattern.

```clojure
(ns example)

(deftrait my-trait [a b])

(impl [x y] (my-trait x y) :foo)
```
```status
Success
```

The number of placeholders much match the number of parameters in the trait.

```clojure
(ns example)

(deftrait my-trait [a b])

(impl [x] (my-trait x) :foo)
```
```status
ERROR: Too few arguments given to trait my-trait . Missing arguments for [b] in (impl [x] (my-trait ...) ...)
```

```clojure
(ns example)

(deftrait my-trait [a b])

(impl [x y z] (my-trait x y z) :foo)
```
```status
ERROR: Too many arguments given to trait my-trait . (z) are extra in (impl [x ...] (my-trait ...) ...)
```

Each trait argument must be a free variable, meaning it must be declared in the
free variable list at the beginning of the block.

```clojure
(ns example)

(deftrait my-trait [a b])

(impl [x y] (my-trait x y') :foo)
```
```status
ERROR: y' is not a free variable in (impl [x ...] (my-trait ...) ...)
```

### Tree Node Pattern

The third element in an `impl` block is a tree-node pattern. This pattern is
meant to match a node in the parse-tree of a program to which this `impl` block
applies.

The pattern we have seen in previous example consists of a keyword (`:foo`). Not
any s-expression would count as a valid pattern. For example, a value, such as a
number or a string would not.

```clojure
(ns example)

(deftrait my-trait [])

(impl [] (my-trait) 42)
```
```status
ERROR: :int is not a valid tree-node pattern given 42 in (impl [] (my-trait) ...)
```

In addition to keywords, patterns can also be symbols.

```clojure
(ns example)

(deftrait my-trait [])

(impl [] (my-trait) some-symbol)
```
```status
Success
```
