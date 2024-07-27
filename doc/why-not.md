* [Why-Not-Explanations](#why-not-explanations)
  * [Failing Rules](#failing-rules)
  * [Explanation Context](#explanation-context)
```clojure
(ns why-not
  (:require [hello :refer [bit]]))

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
(all []
     (bit 2 ! "2 is not a bit value. Only 0 and 1"))

(assert
 (bit 2 ! "2 is not a bit value. Only 0 and 1"))

```
## Explanation Context

A good explanation points to the root cause of the issue. However, sometimes
the root cause by itself is not explanation enough.

As an example, consider we wish to define `bit-stream` as a language of
vectors of bits. We begin by defining `bit-vec`, a predicate that accepts
bit vectors.
```clojure
(all [bv]
     (bit-vec bv ! bv "is not a bit-vector"))
(all [b bv]
     (bit-vec [b & bv]) <-
     (bit b)
     (bit-vec bv))
(all []
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
     (bit-vec bv ! "in" bv))

(assert
 (bit-stream (bitvec [1 0 1]))
 (bit-stream (bitvec [1 0 2 0])
          ! "2 is not a bit value. Only 0 and 1" "in" [1 0 2 0]))
```

