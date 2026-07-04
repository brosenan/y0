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
