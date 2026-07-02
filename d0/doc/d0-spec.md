# $D_0$: A Language for Defining Dynamic Semantics

This is a language spec for the $D_0$ language, a language for defining the
_dynamic semantics_ of programming languages.

Language: `d0`

## Traits

The basic unit of declaration in $D_0$ is a _trait_. Traits in $D_0$ take their
idea from their namesakes in Rust (and type-classes in Haskell), but apply
strictly to parse-trees. A $D_0$ trait corresponds to a $y_0$ predicate, but
while $y_0$ is about determining whether a given (sub)tree is accepted by a
given predicate, $D_0$ is about giving these predicate meaning in the form of
methods that need to be implemented for this tree. This means that if the $y_0$
definition of a language accepts a certain tree as matching predicate $x$, $D_0$
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
ERROR: Invalid trait name  Bad-name . A trait name should only use lower-case letters, digits and dashes in (deftrait Bad-name [])
```

