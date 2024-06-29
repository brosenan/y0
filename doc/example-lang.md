* [Example Language](#example-language)
  * [Expression Types](#expression-types)
  * [Built-in-Operators](#built-in-operators)
  * [`let` Expressions](#`let`-expressions)
  * [Monomorphic Functions](#monomorphic-functions)
    * [Declaring Monomorphic Functions](#declaring-monomorphic-functions)
```clojure
(ns example-lang)

```
# Example Language

In this module we define the semantics of an example language: $y_1$. The
language will syntactically resemble Clojure, but will be statically-typed.

Most of the semantics of the language will revolve around its type system.
In the following we will define it, step by step.

## Expression Types

We begin with defining `typeof`, a predicate to associate types to
expressions. In a way, the predicate `typeof` _defines_ the sub-language
of expressions because everything that can be given a type _is a valid
expression_.

We first define the base-case for this predicate. It uses the [built-in
predicate `inspect`](builtins.md#inspect) to understand the _kind_ of
expression, and calls `typeof-inspected` to pattern-match on the kind.
```clojure
(all [x t]
     (typeof x t) <-
     (exist [k]
            (inspect x k)
            (typeof-inspected k x t)))

```
Now we define the base cases for `typeof-inspected`, starting with the base
case, rejecting kinds that are not supported by a specific rule.
```clojure
(all [k x t]
     (typeof-inspected k x t ! x "is not a y1 expression"))

```
Now things that are not explicitly defined as $y_1$ expressions (so, all
things at this point) will result in this error message.
```clojure
(assert
 (exist [t] (typeof foo t) ! foo "is not a y1 expression"))

```
Next, we add cases for the different literal types.
```clojure
(all [x]
     (typeof-inspected :int x int))
(all [x]
     (typeof-inspected :float x float))
(all [x]
     (typeof-inspected :string x string))

(assert
 (typeof 42 int)
 (typeof 3.141593 float)
 (typeof "foo bar" string))

```
Vectors are assumed to have the same type for all elements. The type of
the first element is inferred and all elements need to match.
```clojure
(all [v t]
     (typeof-inspected :vector v (vector t)) <-
     (all-typesof v t))

```
It uses a new predicate, `all-typesof` to unify the types of all elements in
the vector to a single type `t`.
```clojure
(all [x t]
     (all-typesof x t ! "all-typesof does not support value" x))
(all [x xs t]
     (all-typesof [x & xs] t) <-
     (exist [t']
            (typeof x t)
            (all-typesof xs t')
            (= t t' ! "type mismatch between elements in a vector." x "is" t "while" xs "are" t')))
(all [t]
     (all-typesof [] t))


(assert
 (typeof [1] (vector int))
 (typeof [1 2 3] (vector int))
 (exist [t]
        (typeof [1 2 "three"] t)
        ! "type mismatch between elements in a vector." 2 "is" int
        "while" ["three"] "are" string))

```
## Built-in Operators

$y_0$ defines a few operators that can be used in expressions.

Arithmetic (binary) operators are defined for numeric types (`int` and
`float`), and require that the operands are of the same type. The returnede
type is the same as the operands.
```clojure
(all [a b t]
     (typeof (+ a b) t) <-
     (typeof a t)
     (exist [t']
            (typeof b t')
            (= t t' ! "Type mismatch in +." a "has type" t "while" b "has type" t')
            (numeric-type t)))

```
This definition provides a match for the `typeof` predicate directly. It
requires that both operands are of the same type. Then it requires that the
common type is numeric, using the `numeric-type` predicate that is defined

```clojure
(all [x]
     (numeric-type x ! x "is not a numeric type"))
(all [] (numeric-type int))
(all [] (numeric-type float))

(assert
 (typeof (+ 1 (+ 2 3)) int)
 (typeof (+ 1.1 (+ 2.2 3.3)) float)
 (exist [t]
        (typeof (+ 1 (+ 2.2 3.3)) t)
        ! "Type mismatch in +." 1 "has type" int "while" (+ 2.2 3.3) "has type" float)
 (exist [t]
        (typeof (+ "1" (+ "2" "3")) t)
        ! string "is not a numeric type"))

```
Since we have at least three more operators to define, let's automate the
process using the following [translation rule](statements.md):
```clojure
(all [op]
     (defbinumop op) =>
     (all [a b t]
          (typeof (op a b) t) <-
          (typeof a t)
          (exist [t']
                 (typeof b t')
                 (= t t' ! "Type mismatch in" op ":" a "has type" t "while" b "has type" t')
                 (numeric-type t))))

```
This repeats the rule for `(+ a b)`, but applies it to any operator that
will be defined using `defbinumop`.

Now we can define the rest of the operators and check that they are defined.
```clojure
(defbinumop -)
(defbinumop *)
(defbinumop /)
(defbinumop mod)

(assert
 (typeof (- 1 (* 2 3)) int)
 (typeof (/ 1.1 (mod 2.2 3.3)) float)
 (exist [t]
        (typeof (* 1 (/ 2.2 3.3)) t)
        ! "Type mismatch in" * ":" 1 "has type" int "while" (/ 2.2 3.3) "has type" float)
 (exist [t]
        (typeof (mod "1" (- "2" "3")) t)
        ! string "is not a numeric type"))

```
## `let` Expressions

Similar to Clojure, `let` expressions in $y_1$ have the following structure:
`(let [var-expr-pairs...] expr)` where `var-expr-pairs...` are zero or more
pairs of symbol and an expression and `expr` is an expression.

The type of such an expression is defined using the `typeof-let` predicate.
```clojure
(all [var-expr-pairs expr t]
     (typeof (let var-expr-pairs expr) t) <-
     (typeof-let var-expr-pairs expr t))

```
The base rule for `typeof-let` handles the case where the `let` expression
does not get a vector as its first argument.
```clojure
(all [x expr t]
     (typeof-let x expr t ! "let requires a vector as its first argument." x "is given"))

(assertx
 (exist [t] (typeof (let 2 3) t)
        ! "let requires a vector as its first argument." 2 "is given"))

```
Now we provide rules for a vector of at least two elements.
```clojure
(all [var var-expr var-expr-pairs expr t]
     (typeof-let [var var-expr & var-expr-pairs] expr t) <-
     (inspect var :symbol
              ! "Variable names in let expressions must be symbols." var "is given")
     (exist [var-t]
            (typeof var-expr var-t)
            (given (all [] (typeof var var-t))
                   (typeof-let var-expr-pairs expr t))))


```
Given a variable name `var` and an expression to assign to it `var-expr`,
we start by checking that `var` is a symbol. Then we infer the type of `
var-expr` and then [inject](conditions.md##given-conditions) a rule defining
the type of `var` as the type inferred for `var-expr` before recursing for
the rest of the list.

The termination condition is the case for an empty vector, which simply
infers the type of `let`'s second argument.
```clojure
(all [expr t]
     (typeof-let [] expr t) <-
     (typeof expr t))

```
Finally, we wish to provide an error for a case where the number of element
in the vector is odd.
```clojure
(all [x expr t]
     (typeof-let [x] expr t
                 ! "The vector in a let expression must consist of var,expression pairs." 
                 x "is extra"))

```
Now we can check if our `let` expression behaves as expected.
```clojure
(assert
 (typeof (let [] 42) int)
 (typeof (let [n 42] (+ n 2)) int)
 (typeof (let [n 42.0
               m (+ n 2.0)] (* n m)) float))

```
## Monomorphic Functions

Monomorphic functions are functions for which both the parameter types and
the return type are fully defined with the function.

Monomorphic $y_1$ functions can be _declared_ or _defined_. The former just
provides the signature while the latter provides the signature along with
the implementation. Declared functions are assumed to have their definition
elsewhere, i.e., as built-in or foreign functions.

### Declaring Monomorphic Functions

Declaring a monomorphic function is done by the `declfn` statement. It is
defined using the following translation rule.
```clojure
(all [name params type]
     (declfn name params type) =>
     (assert 
      (exist []
             (inspect name :symbol 
                      ! "A function name must be a symbol." name "is given")
             (inspect params :vector
                      ! "Parameter types need to be provided as a vector." 
                      params "is given")))
     (all [args]
          (typeof (name & args) type) <-
          (exist [arg-types]
                 (typesof args arg-types)
                 (check-types params arg-types args name))))

```
This translation rule translates a `declfn` definition, which takes a name,
a vector of params and a return type to two things:
1. An `assert` block, which performs two assertions: one to make sure the
name is a symbol and the other, to make sure that the parameters are given
as a vector.

These assertions run whenever a `declfn` statement is loaded. We can test
them using "empty" `given` conditions.
```clojure
(assert
 (given (declfn foo [int float] string))
 (given (declfn :foo [int float] string)
        ! "A function name must be a symbol." :foo "is given")
 (given (declfn foo (int float) string)
        ! "Parameter types need to be provided as a vector."
        (int float) "is given"))

```
The second statement generated by the rule defines a rule for `typeof`,
which matches against any list that starts with `name`. The rule calls two
predicates that we have yet to define, so this is a good place to do so.

`typesof` takes a list of expressions and provides a list of their types.
```clojure
(all [xs ts]
     (typesof xs ts ! "typesof requires a list as its first argument." xs "is given"))
(all [x xs t ts]
     (typesof (x & xs) (t & ts)) <-
     (typeof x t)
     (typesof xs ts))
(all []
     (typesof () ()))

(assert
 (typesof (1 "two" 3.0) (int string float)))

```
`check-types` takes a vector of expected types (`params`) and a list of
inferred types (`arg-tyeps`) and succeeds if all they match. iThe `args`
and `name` of the function are given for reference.
```clojure
(all [params arg-types args name]
     (check-types params arg-types args name
                  ! "check-types requires params to be provided as a vector."
                  params "is given"))

(all [param params arg-types args name]
     (check-types [param & params] arg-types args name) <-
     (exist [type types a as]
            (= arg-types (type & types)
               ! "Too few arguments in call to function" name)
            (= args (a & as)
               ! "Too few arguments in call to function" name)
            (= param type ! "Type mismatch in parameter for function" name
               ": argument" a "is of type" type "but" param "is required")
            (check-types params types as name)))

(all [arg-types args name]
     (check-types [] arg-types args name) <-
     (= args () ! "Too many arguments in call to function" name
        ": extra arguments:" args))

```
We focus our discussion on the second rule. Its head pattern-matches on the
first argument only, in order to provide meaningful explanations when the
other arguments in the goal do not match. After destructing each of them,
the correctness of the specific type is checked using the `=` predicate.
```clojure
(assert
 (check-types [int float string] (int float string) (a b c) foo)
 (check-types [int float string] (int int string) (a b c) foo
              ! "Type mismatch in parameter for function" foo
              ": argument" b "is of type" int "but" float "is required")
 (check-types [int float string] (int float) (a b) foo
              ! "Too few arguments in call to function" foo)
 (check-types [int float string] (int float string (vector int)) (a b c d) foo
              ! "Too many arguments in call to function" foo
                ": extra arguments:" (d)))

```
So now we are ready to declare a few functions and use them in expressions.
We will start with conversion functions between primitive types.
```clojure
(declfn float [int] float)
(declfn round [float] int)
(declfn ceil [float] int)
(declfn floor [float] int)
(declfn trunc [float] int)
(declfn parse-int [string] int)
(declfn parse-float [string] float)
(declfn as-decimal [int] string)
(declfn as-hex [int] string)
(declfn as-scientific [float] string)


```
Now let us use some of these functions in expressions.
```clojure
(assert
 (typeof (round (+ (parse-float "3.0") (float 2))) int)
 (typeof (round (+ (parse-int "3.0") (float "2"))) int
         ! "Type mismatch in parameter for function" float
         ": argument" "2" "is of type" string "but" int "is required"))
```
