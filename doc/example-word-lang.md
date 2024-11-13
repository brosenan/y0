* [Word Example](#word-example)
  * [The `word` Predicate](#the-`word`-predicate)
  * [Defining Words](#defining-words)
  * [Exporting Words](#exporting-words)
  * [Where To Next?](#where-to-next?)
```clojure
(ns example-word-lang)

```
# Word Example

To demonstrate imports and exports, this module defines a simple language of
word (symbols). It allows for words to be defined. A word that is defined
will them be accepted by the `word` predicate.

## The `word` Predicate

We begin by defining the `word` predicate's base case.
```clojure
(all [w]
     (word w ! w "is not a word"))

(assert
 (word foo ! foo "is not a word"))

```
## Defining Words

Next, we allow for the definition of new words.
```clojure
(all [w]
     (defword w) =>
     (all []
          (word w)))

(assert
 (given (defword foo)
        (word foo)))

```
## Exporting Words

Now, we define the mechanism that allows words to be exported.
```clojure
(all [w]
     (defword w) =>
     (export [w' w]
             (all []
                  (word w') <- (word w))))

```
This contributes an `export` statement for every use of `defword` (this could
have been placed within the same translation rule as the original definition,
but we chose to split it here for didactic reasons).

The `export` statement binds `w'` to be the _imported version_ of `w` when
imported, i.e., `w'` will be a symbol with the same name as `w`, but will
have the namespace of the importing module instead of the exporting module in
`w`.

The statement itself within the body of the `export` statement is a deduction
rule which states that `w'` is a word if `w` is a word.

## Where To Next?

This example continues in [a module using this language](example-words.md)
