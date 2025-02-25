* [Why-Not-Explanations](#why-not-explanations)
  * [Failing Rules](#failing-rules)
  * [Explanation Cause](#explanation-cause)
  * [Context for Assertions](#context-for-assertions)
```clojure
(ns why-not
  (:require [hello :refer [bit]]
            [statements :refer [foo]]))

```
# Why-Not Explanations

One of the most important features of $y_0$, and the one that gave it its
name, is its ability to convey explanations as to why a certain AST _does
not_ comply with the defined language.

In the following sections we describe $y_0$'s support for why-not
explanations.

## Failing Rules

Rules that fail need to provide an explanation. We have already seen one
example of this, in the base-rule of
[the `bit` predicate, defined in the introduction](hello.md#predicates).
This provided the explanation that anything that is not explicitly defined
as a bit in any of the following rules (i.e., `0` or `1`) is not a bit.
```clojure
(assert
 (bit 5 ! "Expected a bit, received" 5))

```
We can define another failing rule for `bit` rejecting yet another value
with a different explanation
```clojure
(fact
     (bit 2 ! "2 is not a bit value. Only 0 and 1"))

(assert
 (bit 2 ! "2 is not a bit value. Only 0 and 1"))

```
## Explanation Cause

A good explanation points to the root cause of the issue. However, sometimes
the root cause by itself is not explanation enough. Sometimes the root cause
explanation is too narrow and in order to give a good explanation we need to
begin one or more levels above, explain what fails and refer to the
underlying cause.

As an example, consider we wish to define `bit-stream` as a language of
streams of bits. We begin by defining `bit-vec`, a predicate that accepts
bit vectors.
```clojure
(all [bv]
     (bit-vec bv ! bv "is not a bit-vector"))
(all [b bv]
     (bit-vec [b & bv]) <-
     (bit b)
     (bit-vec bv))
(fact
     (bit-vec []))

```
This predicate accepts vectors of bits and rejects anything else.
```clojure
(assert
 (bit-vec [1 0 1 0])
 (bit-vec [1 0 2 0]
          ! "2 is not a bit value. Only 0 and 1")
 (bit-vec :foo ! :foo "is not a bit-vector"))

```
Now we define `bit-stream`. The AST `(bitvec vec...)` is defined as a bit
stream.
```clojure
(all [bs]
     (bit-stream bs ! bs "is not a bit-stream"))
(all [bv]
     (bit-stream (bitvec bv)) <-
     (bit-vec bv ! "invalid bit-stream" ? "because:" !? "so please fix it"))

```
The `!?` symbol is used as a placeholder for the root cause, which is
concatenated in its place.

The `?` symbol is used here as a placeholder for the _subject_, i.e., the
term on which this rule pattern-matched. As can be seen in the example
below, it matches the `(bitvec bits...)` pattern.

```clojure
(assert
 (bit-stream (bitvec [1 0 1]))
 (bit-stream (bitvec [1 0 2 0])
          ! "invalid bit-stream" (bitvec [1 0 2 0])
             "because:" "2 is not a bit value. Only 0 and 1" "so please fix it"))

```
If the `!?` symbol is omitted, the higher-level explanation is appenderd to
the cause.
```clojure
(all [bv]
     (bit-stream (bitvec' bv)) <-
     (bit-vec bv ! "in" ?))
(assert
 (bit-stream (bitvec' [1 0 1]))
 (bit-stream (bitvec' [1 0 2 0])
             ! "2 is not a bit value. Only 0 and 1" "in" (bitvec' [1 0 2 0])))

```
## Context for Assertions

One specific case in which context is important are assertion blocks that
are the result of a [translation rule](statements.md#translation-rules)
working on some statement. In such cases, we often wish to know which
statement was the origin for the assertion.

$y_0$ does so automatically. It tracks, for each statement that is generated
by a translation rule, what statement was its origin. Then it provides this
origin as context to assertions within any generated assertion block.

For example, let us consider a `defoo` definition, similar to the one
defined in our discussion of
[translation rules](statements.md#translation-rules). This version, however,
adds a restriction that a `foo` must be a symbol.
```clojure
(all [x]
     (defoo x) =>
     (assert
      (is-symbol x))
     (fact (foo x)))

```
Where `is-symbol` is defined as follows:
```clojure
(all [x]
     (is-symbol x) <-
     (inspect x :symbol ! x "is not a symbol"))

```
The statement `defoo` which is defined here translates to an assertion that
the defined thing is a symbol. Then it contributes a rule to the `foo`
predicate. Please note that `defoo` here is separate from the original one
as it is defined witin a different namespace. However, we import `foo` to
reuse the symbol from its original definition.

Now, `defoo` statements with non-symbols will fail.
```clojure
(assert
 (given (defoo x)
        (foo x))
 (given (defoo "x") ! "x" "is not a symbol" "in" (defoo "x")))

```
In case where an assert-block is resulting from a chained translation, the
original statement is presented as context.

For example, let us define `defoo'` as a statement that translates to
`defoo`.
```clojure
(all [x]
     (defoo' x) => (defoo x))

```
An error in `defoo'` will be reported with `defoo'`, rather than `defoo` as
context.
```clojure
(assert
 (given (defoo' x)
        (foo x))
 (given (defoo' "x") ! "x" "is not a symbol" "in" (defoo' "x")))
```

