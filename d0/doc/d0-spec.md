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

#### Complex Patterns

Patterns can consist of vectors (`[]`) or lists (`()`). For each, they can be
either _forms_, if the first element is a symbol (that is not a free variable)
or a keyword.

The following example shows valid patters.

```clojure
(ns example)

(deftrait my-trait [])

(impl [a b] (my-trait) (some-symbol a b))
(impl [x y z] (my-trait) [some-symbol x y z])
(impl [] (my-trait) ())
(impl [] (my-trait) [])
(impl [a b] (my-trait) (a b))
(impl [x y z] (my-trait) [x y z])
```
```status
Success
```

Other than the first element, which may or may not be a free variable, all other
elements must be free variables.

```clojure
(ns example)

(deftrait my-trait [])

(impl [a b] (my-trait) (some-symbol a b'))
```
```status
ERROR: b' is not a free variable in (impl [a ...] (my-trait) ...)
```

```clojure
(ns example)

(deftrait my-trait [])

(impl [a b] (my-trait) [some-symbol a b'])
```
```status
ERROR: b' is not a free variable in (impl [a ...] (my-trait) ...)
```

The first element must be a symbol (free variable or not), or a keyword.

```clojure
(ns example)

(deftrait my-trait [])

(impl [a] (my-trait) (a))
(impl [a] (my-trait) [a])
(impl [] (my-trait) (sym))
(impl [] (my-trait) [sym])
(impl [] (my-trait) (:a))
(impl [] (my-trait) [:a])
```
```status
Success
```

But not anything else.

```clojure
(ns example)

(deftrait my-trait [])

(impl [] (my-trait) (42))
```
```status
ERROR: Invalid form-head 42 in pattern in (impl [] (my-trait) ...)
```

```clojure
(ns example)

(deftrait my-trait [])

(impl [] (my-trait) ["42"])
```
```status
ERROR: Invalid form-head 42 in pattern in (impl [] (my-trait) ...)
```

A pattern can be variadic. Variadic patterns use the `&` symbol before the last
variable. In this case, the last variable represents the tail of the list of
vector.

```clojure
(ns example)

(deftrait my-trait [])

(impl [a as] (my-trait) (a & as))
(impl [a as] (my-trait) [a & as])
```
```status
Success
```

```clojure
(ns example)

(deftrait my-trait [])

(impl [a as b] (my-trait) (a & as b))
```
```status
ERROR: & must be followed by a single variable. Got: (as b) in (impl [a ...] (my-trait) ...)
```

```clojure
(ns example)

(deftrait my-trait [])

(impl [a as b] (my-trait) [a & as b])
```
```status
ERROR: & must be followed by a single variable. Got: [as b] in (impl [a ...] (my-trait) ...)
```

```clojure
(ns example)

(deftrait my-trait [])

(impl [a as] (my-trait) (a & as'))
```
```status
ERROR: as' is not a free variable in (impl [a ...] (my-trait) ...)
```

```clojure
(ns example)

(deftrait my-trait [])

(impl [a as] (my-trait) [a & as'])
```
```status
ERROR: as' is not a free variable in (impl [a ...] (my-trait) ...)
```

#### Tree Node Pattern Uniqueness

Tree-node patterns must be unique in the sense that it is not allowed for two
`impl` blocks to exist for the same trait, for two, effectively identical
patterns. This is important to make the semantics well-defined, by avoiding
situations in which there is more than one way to interpret a parse-tree.

But what does it mean for two patterns to be effectively identical? The
following examples will try to answer this question.

Two simple patterns are identical if they are using the same symbol or keyword.

```clojure
(ns example)

(deftrait my-trait [])

(impl [] (my-trait) foo)
(impl [] (my-trait) foo)
```
```status
ERROR: The rule for (impl {:trait example/my-trait, :pattern {:symbol example/foo}}) conflicts with a previous rule defining (impl {:trait example/my-trait, :pattern {:symbol example/foo}}) in predicate impl with arity 1
```

Note: due to a current limitation in $y_0$, the error message for such conflicts
talk about the internal representation rather than the source code features that
cause it. At this point we disregard the messages themselves and just regard
whether an error was reported or not.

```clojure
(ns example)

(deftrait my-trait [])

(impl [] (my-trait) :foo)
(impl [] (my-trait) :foo)
```
```status
ERROR: The rule for (impl {:trait example/my-trait, :pattern {:keyword :foo}}) conflicts with a previous rule defining (impl {:trait example/my-trait, :pattern {:keyword :foo}}) in predicate impl with arity 1
```

This uniquness is specific per-trait, so defining instances for the same pattern
for different traits is completely allowed.

```clojure
(ns example)

(deftrait trait1 [])
(deftrait trait2 [])

(impl [] (trait1) :foo)
(impl [] (trait2) :foo)
(impl [] (trait1) foo)
(impl [] (trait2) foo)
```
```status
Success
```

The patterns for empty lists and vectors conflict for the same kind for the same
trait.

```clojure
(ns example)

(deftrait my-trait [])

(impl [] (my-trait) [])
(impl [] (my-trait) [])
```
```status
ERROR: The rule for (impl {:trait example/my-trait, :pattern {:vetor :empty}}) conflicts with a previous rule defining (impl {:trait example/my-trait, :pattern {:vetor :empty}}) in predicate impl with arity 1
```

```clojure
(ns example)

(deftrait my-trait [])

(impl [] (my-trait) ())
(impl [] (my-trait) ())
```
```status
ERROR: The rule for (impl {:trait example/my-trait, :pattern {:list :empty}}) conflicts with a previous rule defining (impl {:trait example/my-trait, :pattern {:list :empty}}) in predicate impl with arity 1
```

Form heads need to be unique.

```clojure
(ns example)

(deftrait my-trait [])

(impl [] (my-trait) (foo))
(impl [] (my-trait) (foo))
```
```status
ERROR: The rule for (impl {:trait example/my-trait, :pattern {:list :nonempty, :head {:value example/foo}, :tail :empty}}) conflicts with a previous rule defining (impl {:trait example/my-trait, :pattern {:list :nonempty, :head {:value example/foo}, :tail :empty}}) in predicate impl with arity 1
```

```clojure
(ns example)

(deftrait my-trait [])

(impl [] (my-trait) [foo])
(impl [] (my-trait) [foo])
```
```status
ERROR: The rule for (impl {:trait example/my-trait, :pattern {:vector :nonempty, :head {:value example/foo}, :tail :empty}}) conflicts with a previous rule defining (impl {:trait example/my-trait, :pattern {:vector :nonempty, :head {:value example/foo}, :tail :empty}}) in predicate impl with arity 1
```

If the head is a free variable, the name of the variable doesn't matter for
uniqueness.

```clojure
(ns example)

(deftrait my-trait [])

(impl [foo] (my-trait) (foo))
(impl [bar] (my-trait) (bar))
```
```status
ERROR: The rule for (impl {:trait example/my-trait, :pattern {:list :nonempty, :head :any, :tail :empty}}) conflicts with a previous rule defining (impl {:trait example/my-trait, :pattern {:list :nonempty, :head :any, :tail :empty}}) in predicate impl with arity 1
```

```clojure
(ns example)

(deftrait my-trait [])

(impl [foo] (my-trait) [foo])
(impl [bar] (my-trait) [bar])
```
```status
ERROR: The rule for (impl {:trait example/my-trait, :pattern {:vector :nonempty, :head :any, :tail :empty}}) conflicts with a previous rule defining (impl {:trait example/my-trait, :pattern {:vector :nonempty, :head :any, :tail :empty}}) in predicate impl with arity 1
```

Two patterns of the same head and a different arity can co-exist.

```clojure
(ns example)

(deftrait my-trait [])

(impl [a b] (my-trait) (foo a b))
(impl [a b c] (my-trait) (foo a b c))
(impl [a b c d] (my-trait) (foo a b c & d))
```
```status
Success
```

```clojure
(ns example)

(deftrait my-trait [])

(impl [a b] (my-trait) [foo a b])
(impl [a b c d] (my-trait) [foo a b c & d])
```
```status
Success
```

### Type Definitions

Following the parse-tree pattern, definitions that match the declarations in the
corresponding trait follow. Specifically, each [type
declaration](#associated-type-declarations) in the trait must have a
corresponding type definition, introduced with the `deftype` keyword, providing
the concrete type for the associated type.

```clojure
(ns example)

(deftrait my-trait []
  (decltype T))

(impl [] (my-trait) :pattern
  (deftype T Int64))
```
```status
Success
```

The definitions must match the trait's declarations _in the same order_. When a
trait declares more than one associated type, the corresponding `deftype`
definitions must appear in the same order in which they were declared.

```clojure
(ns example)

(deftrait my-trait []
  (decltype T)
  (decltype S))

(impl [] (my-trait) :pattern
  (deftype T Int64)
  (deftype S Int64))
```
```status
Success
```

Providing the definitions in a different order than the declarations is an
error.

```clojure
(ns example)

(deftrait my-trait []
  (decltype T)
  (decltype S))

(impl [] (my-trait) :pattern
  (deftype S Int64)
  (deftype T Int64))
```
```status
ERROR: Expected definition of type T but found definition for type S instead in (impl [] (my-trait) ...)
```

A declaration with no corresponding definition is an error.

```clojure
(ns example)

(deftrait my-trait []
  (decltype T)
  (decltype S))

(impl [] (my-trait) :pattern
  (deftype T Int64))
```
```status
ERROR: Missing definitions for the trait's declarations ((decltype ...)) in (impl [] (my-trait) ...)
```

Similarly, a definition with no corresponding declaration is an error.

```clojure
(ns example)

(deftrait my-trait []
  (decltype T))

(impl [] (my-trait) :pattern
  (deftype T Int64)
  (deftype S Int64))
```
```status
ERROR: Type definition for S doesn't have a matching declaration in (impl [] (my-trait) ...)
```

The types used in a type definition must be valid types.

```clojure
(ns example)

(deftrait my-trait []
  (decltype T))

(impl [] (my-trait) :pattern
  (deftype T FooType))
```
```status
ERROR: FooType is not a declared type in definition of T in (impl [] (my-trait) ...)
```

#### Using Associated Types in Type Definitions

Type definitions can use associated types. The tree-nodes for the associated
types can come either from the trait parameters or the pattern.

```clojure
(ns example)

(deftrait my-trait [foo]
  (decltype T))

(impl [bar] (my-trait bar) :pattern
  (deftype T (T bar)))
```
```status
Success
```

```clojure
(ns example)

(deftrait my-trait []
  (decltype T))

(impl [foo] (my-trait) (:pattern foo)
  (deftype T (T foo)))
```
```status
Success
```

```clojure
(ns example)

(deftrait my-trait []
  (decltype T))

(impl [foo] (my-trait) [:pattern foo]
  (deftype T (T foo)))
```
```status
Success
```

This includes cases where the variable is in the pattern's head position.

```clojure
(ns example)

(deftrait my-trait []
  (decltype T))

(impl [foo] (my-trait) (foo)
  (deftype T (T foo)))
```
```status
Success
```

```clojure
(ns example)

(deftrait my-trait []
  (decltype T))

(impl [foo] (my-trait) [foo]
  (deftype T (T foo)))
```
```status
Success
```

### Method Definitions

Every [method declaration](#method-declarations) in the trait mentioned in the
`impl` block must be matched with a method definition.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [] Int64))

(impl [] (my-trait) :pattern
  (defmethod foo [] 42))
```
```status
Success
```

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [] Int64))

(impl [] (my-trait) :pattern)
```
```status
ERROR: Missing definitions for the trait's declarations ((declmethod ...)) in (impl [] (my-trait) ...)
```

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [] Int64))

(impl [] (my-trait) :pattern
  (deftype Foo Int64))
```
```status
ERROR: Definition of type Foo matches the wrong declaration (declmethod foo [] ...) in (impl [] (my-trait) ...)
```

```clojure
(ns example)

(deftrait my-trait []
  (decltype Foo))

(impl [] (my-trait) :pattern
  (defmethod bar [] 42))
```
```status
ERROR: Definition for method bar matches the wrong declaration (decltype Foo) in (impl [] (my-trait) ...)
```

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [] Int64))

(impl [] (my-trait) :pattern
  (defmethod bar [] 42))
```
```status
ERROR: Expected definition of method foo but got definition for bar in (impl [] (my-trait) ...)
```

The argument list should be a vector containing symbols, which must match the
length of the parameter types vector provided in the method declaration.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [Int64 Int64] Int64))

(impl [] (my-trait) :pattern
  (defmethod foo [a b] 42))
```
```status
Success
```

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [Int64 Int64] Int64))

(impl [] (my-trait) :pattern
  (defmethod foo [a] 42))
```
```status
ERROR: Missing params for types [Int64] in definition of method foo in (impl [] (my-trait) ...)
```

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [Int64 Int64] Int64))

(impl [] (my-trait) :pattern
  (defmethod foo [a b c] 42))
```
```status
ERROR: Parameter c is not matched by a type in the declaration in definition of method foo in (impl [] (my-trait) ...)
```

The body of a method definition is an _expression_. Due to the importance and
complexity of the topic, we dedicate a whoe section to
[expressions](#expressions).

### Definition Matching

In a $y_0$ semantic definition of a language, many rules are defined from parts
of the program's parse-tree, using [translation
rules](https://github.com/brosenan/y0/blob/main/doc/statements.md#translation-rules).

As a result, many tree-nodes are associated not only with a predicate that
matches them, but also with a _definition_, another node that contributed the
definition that created the rule that matched this node.

As a conceptual example, the definition of a function can contribute the rules
that make a call to this function valid. Therefore, the node responsible to the
call is associated with the function's definition.

In `D0`, we leverage this relationship to gain more information about a node. In
the case of a function, this is how we can use the function's body to interpret
the call.

This is done through `match-def` blocks insde `impl` blocks. A `match-def` block
has the syntax `(match-def [vars...] def-pattern defs...)`, where `vars`
is a vector of symbols, `def-pattern` is a pattern matching the definition's
parse-tree, where `vars` are treated as free variables, and `defs` are the
associated type and method definitions for the `impl`, given a matching pattern.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [] Int64))

(impl [] (my-trait) :pattern
  (match-def [x] [:some-def x]
    (defmethod foo [] (foo x)))
  (defmethod foo [] 42))
```
```status
Success
```

In the above example we provide two different definitions for method `foo`. The
default definition, which returns 42, and a definition that will take effect if
the `:pattern` is associated with the definition of the form `[:some-def x]`
(for some `x`). In that case, the call recurses to `foo` in the definition.

The provided `def-pattern` must be a valid pattern, following the same rules as
in the tree-node pattern at the head of the `impl` block.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [] Int64))

(impl [] (my-trait) :pattern
  (match-def [] [:some invalid pattern]
    (defmethod foo [] (foo x)))
  (defmethod foo [] 42))
```
```status
ERROR: invalid is not a free variable in definition matching pattern [:some invalid pattern] in (impl [] (my-trait) ...)
```

The definitions in a `match-def` block must match all _pending_ definitions, not
yet addressed by previous definitions at the `impl`-block level.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [] Int64))

(impl [] (my-trait) :pattern
  (defmethod foo [] 42) ;; Now the default definition comes before the
                        ;; `match-def` block.
  (match-def [x] [:some-def x]
    (defmethod foo [] (foo x))))
```
```status
ERROR: Unexpected definition of method foo in definition matching for [:some-def x] in (impl [] (my-trait) ...)
```

There can be any number of definitions matchings in an `impl` block.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [] Int64))

(impl [] (my-trait) :pattern
  (match-def [x] [:some-def x]
    (defmethod foo [] (foo x)))
  (match-def [y] [:some-other-def y]
    (defmethod foo [] (foo y)))
  (defmethod foo [] 42))
```
```status
Success
```

When default definitions do not make sense, i.e., when the pattern matched by
the `impl` block _must_ have a definition, the default definitions can be
replaced by the `:no-default-defs` keyword.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [] Int64))

(impl [] (my-trait) :pattern
  (match-def [x] [:some-def x]
    (defmethod foo [] (foo x)))
  (match-def [y] [:some-other-def y]
    (defmethod foo [] (foo y)))
  :no-default-defs)
```
```status
Success
```

## Expressions

Expressions appear in bodies of methods. Here we specify what they can consist
of.

Note: `D0` performs type checking only when applied to a specific parse-tree.
Therefore, we will not discuss the types of expressions here, although every
expression is guaranteed to have a well-defined type.

As a baseline, the following example shows an invalid expression.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [] Int64))

(impl [] (my-trait) :pattern
  (defmethod foo [] invalid-expression))
```
```status
ERROR: Invalid expression invalid-expression in definition of method foo in (impl [] (my-trait) ...)
```

### Literals

Numeric and string literals are valid expressions.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod int-literal [] Int64)
  (declmethod float-literal [] Float64)
  (declmethod string-literal [] String))

(impl [] (my-trait) :pattern
  (defmethod int-literal [] 42)
  (defmethod float-literal [] 3.1415)
  (defmethod string-literal [] "foobar"))
```
```status
Success
```

### Parameters

Method parameters are considered valid expressions.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [Int64 Float64] Int64))

(impl [] (my-trait) :pattern
  (defmethod foo [a b] a))
```
```status
Success
```

`D0` makes a distinction between _expressions_ and _tree-nodes_. Tree nodes are
represented with symbols that are first introduced as free variables and are
then bound to either a trait argument or a part of a pattern, whereas
expressions are discussed here. When trying to use a tree-node placeholder as an
expression, we get a dedicated error message.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [Int64 Float64] Int64))

(impl [x y] (my-trait) [:pattern x y]
  (defmethod foo [a b] x))
```
```status
ERROR: x is a tree-node placeholder, not an expression in definition of method foo in (impl [x ...] (my-trait) ...)
```

### Method Invocations

Expressions may consist of method invocations. A method invocation has the form
`(method tree-node args...)`, where `args` are zero or more expressions.

```clojure
(ns example)

(deftrait trait-a []
  (declmethod a [Int64] Int64))
(deftrait trait-b []
  (declmethod b [Int64] Int64))

(impl [x] (trait-b) [:pattern x]
  (defmethod b [p] (a x p)))
```
```status
Success
```

The method being called must be declared in a trait.

```clojure
(ns example)

(deftrait trait-a []
  (declmethod a [Int64] Int64))
(deftrait trait-b []
  (declmethod b [Int64] Int64))

(impl [x] (trait-b) [:pattern x]
  (defmethod b [p] (foo x p)))
```
```status
ERROR: Undefined method foo in definition of method b in (impl [x] (trait-b) ...)
```

Recursion through the parse-tree is supported.

```clojure
(ns example)

(deftrait trait-b []
  (declmethod b [Int64] Int64))

(impl [x] (trait-b) [:pattern x]
  (defmethod b [p] (b x p)))
```
```status
Success
```

The first element after the name in a method invocation must be a tree-node
placeholder.

```clojure
(ns example)

(deftrait trait-a []
  (declmethod a [Int64] Int64))
(deftrait trait-b []
  (declmethod b [Int64] Int64))

(impl [x] (trait-b) [:pattern x]
  (defmethod b [p] (a p p)))
```
```status
ERROR: p does not represent a parse-tree node in this context in call to method a in definition of method b in (impl [x] (trait-b) ...)
```

The number of arguments must match that of types defined in the method
declaration.

```clojure
(ns example)

(deftrait trait-a []
  (declmethod a [Int64] Int64))
(deftrait trait-b []
  (declmethod b [Int64] Int64))

(impl [x] (trait-b) [:pattern x]
  (defmethod b [p] (a x)))
```
```status
ERROR: Too few arguments in method call. Missing values of types [Int64] in call to method a in definition of method b in (impl [x] (trait-b) ...)
```

```clojure
(ns example)

(deftrait trait-a []
  (declmethod a [Int64] Int64))
(deftrait trait-b []
  (declmethod b [Int64] Int64))

(impl [x] (trait-b) [:pattern x]
  (defmethod b [p] (a x p p)))
```
```status
ERROR: Too many arguments in method call. Arguments (p) are extra in call to method a in definition of method b in (impl [x] (trait-b) ...)
```

All arguments must be valid expressions.

```clojure
(ns example)

(deftrait trait-a []
  (declmethod a [Int64] Int64))
(deftrait trait-b []
  (declmethod b [Int64] Int64))

(impl [x] (trait-b) [:pattern x]
  (defmethod b [p] (a x not-an-expression)))
```
```status
ERROR: Invalid expression not-an-expression in call to method a in definition of method b in (impl [x] (trait-b) ...)
```

### `let` Expressions

`let` expressions have similar syntax to their Clojure counterparts, but they
have a slightly different role. By defining variables explicitly, `let`
expressions force their values to be computed eagerly. This is not the case with
method invocations, where due to inlining the arguments are actually computed
lazily.

A `let` expression has the form `(let [var vexpr...] expr)`, with any number of
`var vexpr` pairs. The `var`s are added to the scope of `expr`.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [Int64 Float64] Int64))

(impl [n] (my-trait) [:pattern n]
  (defmethod foo [a b]
    (let [x a
          y b]
          (foo n x y))))
```
```status
Success
```

The number of elements in the bindings vector (following the `let` symbol) must
be even.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [Int64 Float64] Int64))

(impl [n] (my-trait) [:pattern n]
  (defmethod foo [a b]
    (let [x a
          y]
          (foo n x y))))
```
```status
ERROR: An odd number of elements in a let-bindings block. y has no assigned value in definition of method foo in (impl [n] (my-trait) ...)
```

In the bindings, the variables must be symbols.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [Int64 Float64] Int64))

(impl [n] (my-trait) [:pattern n]
  (defmethod foo [a b]
    (let [:x a
          :y b]
          (foo n :x :y))))
```
```status
ERROR: Invalid variable name :x (symbol expected) in binding of let expression in definition of method foo in (impl [n] (my-trait) ...)
```

The expressions must be valid `D0` expressions.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [Int64 Float64] Int64))

(impl [n] (my-trait) [:pattern n]
  (defmethod foo [a b]
    (let [x a
          y not-an-expression]
          (foo n x y))))
```
```status
ERROR: Invalid expression not-an-expression in binding of variable y in definition of method foo in (impl [n] (my-trait) ...)
```

Finally, `expr` must be a valid expression.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [Int64 Float64] Int64))

(impl [n] (my-trait) [:pattern n]
  (defmethod foo [a b]
    (let [x a
          y b]
          invalid-expression)))
```
```status
ERROR: Invalid expression invalid-expression in definition of method foo in (impl [n] (my-trait) ...)
```

### `if` Expressions

An `if` expression is a conditional expression, similar to other functional
languages.

Its syntax is as follows: `(if cond then else)`, where all parameters are
expressions, where `cond` is expected to evaluat to `Bool` (not checked
statically) and `then` and `else` are expected to evaluate to the same type
(again, not checked statically).

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [] Int64)
  (declmethod bar? [] Bool))

(impl [x y] (my-trait) [:pattern x y]
  (defmethod foo []
    (if (bar? x)
      (foo x)
      (foo y)))
  (defmethod bar? []
    (bar? y)))
```
```status
Success
```

Both `cond`, `then` and `else` must be valid expressions.

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [] Int64))

(impl [x y] (my-trait) [:pattern x y]
  (defmethod foo []
    (if invalid-cond-expression
      (foo x)
      (foo y))))
```
```status
ERROR: Invalid expression invalid-cond-expression in definition of method foo in (impl [x ...] (my-trait) ...)
```

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [] Int64)
  (declmethod bar? [] Bool))

(impl [x y] (my-trait) [:pattern x y]
  (defmethod foo []
    (if (bar? x)
      invalid-then-expression
      (foo y)))
  (defmethod bar? []
    (bar? y)))
```
```status
ERROR: Invalid expression invalid-then-expression in definition of method foo in (impl [x ...] (my-trait) ...)
```

```clojure
(ns example)

(deftrait my-trait []
  (declmethod foo [] Int64)
  (declmethod bar? [] Bool))

(impl [x y] (my-trait) [:pattern x y]
  (defmethod foo []
    (if (bar? x)
      (foo x)
      invalid-else-expression))
  (defmethod bar? []
    (bar? y)))
```
```status
ERROR: Invalid expression invalid-else-expression in definition of method foo in (impl [x ...] (my-trait) ...)
```

### `with` Expressions

Similar to how definitions can be injected into contexts in $y_0$ using the
[`given`](https://github.com/brosenan/y0/blob/main/doc/conditions.md#given-conditions)
condition, in `D0` it is possible to inject definitions, namely `impl` blocks,
into contexs using `with` expressions.

The syntax is `(with (impl ...) expr)`.

```clojure
(ns example)

(deftrait trait1 []
  (declmethod foo [] Int64))
(deftrait trait2 []
  (declmethod bar [] Int64))

(impl [n] (trait1) [:pattern n]
  (defmethod foo []
    (with (impl [] (trait2) :something
            (defmethod bar [] 42))
      (foo n))))
```
```status
Success
```

In the above example we define a local `impl` for pattern `:something` for trait
`trait2`, returning 42 from `bar`. Semantically, any calls `(bar :something)`
from within `(foo n)` will return 42, but will remain undefied outside of `(foo
n)`.

The `impl` definition must be a valid one.

```clojure
(ns example)

(deftrait trait1 []
  (declmethod foo [] Int64))

(impl [n] (trait1) [:pattern n]
  (defmethod foo []
    (with (impl [] (trait-that-doesnt-exist) :something
            (defmethod bar [] 42))
      (foo n))))
```
```status
ERROR: (trait-that-doesnt-exist) is not a trait in (impl [] (trait-that-doesnt-exist) ...)
```

Similarly, the underlying expression must be a valid expression.

```clojure
(ns example)

(deftrait trait1 []
  (declmethod foo [] Int64))
(deftrait trait2 []
  (declmethod bar [] Int64))

(impl [n] (trait1) [:pattern n]
  (defmethod foo []
    (with (impl [] (trait2) :something
            (defmethod bar [] 42))
      bad-expression)))
```
```status
ERROR: Invalid expression bad-expression in definition of method foo in (impl [n] (trait1) ...)
```

The pattern in the enclosed `impl` block may consist of a tree-node. This is
useful for providing values to variables, where the tree-node represents the
variable being assigned.

```clojure
(ns example)

(deftrait trait1 []
  (declmethod foo [] Int64))
(deftrait trait2 []
  (declmethod bar [] Int64))

;; This is an instance block for the symbol "v".
(impl [] (trait2) v
  (defmethod bar [] 0))

(impl [v x] (trait1) [:pattern v x]
  (defmethod foo []
    ;; This is an instance definition for v, a placeholder for whatever node is
    ;; in the first position of [:pattern ...]
    (with (impl [] (trait2) v
            (defmethod bar [] 42))
      (foo x))))
```
```status
Success
```
