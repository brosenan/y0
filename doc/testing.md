  * [Running Example](#running-example)
  * [Testing for Success](#testing-for-success)
  * [Testing for Failure](#testing-for-failure)
  * [Recoverable and Unrecoverable Assertions](#recoverable-and-unrecoverable-assertions)
    * [Skipping Recoverable Assertion Blocks](#skipping-recoverable-assertion-blocks)
```clojure
(ns y0.testing-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.rules :refer [apply-assert-block add-rule *error-target*
                              apply-statement *skip-recoverable-assertions*]]
            [y0.core :refer [all assert ! assert!]]
            [y0.status :refer [->s ok let-s]]))

```
Testing is an important aspect of any programming environment, and it is for
this reason that we bake testing into $y_0$, rather than leaving it as an
afterthought.

$y_0$ testing is based on _assert blocks_, or the form `(assert
assertions...)`. Each assertion in `asserts` is a goal, expecting to either
succeed or fail. In case of failure, an expected explanation is provided
using the `!` symbol.

The function `apply-assert-block` takes a predstore, an assert-block, a map
of free variables and a `recoverable` flag (which will be discussed later)
and returns the given predstore if all assertions have passed, or an
appropriate explanation if something failed.

## Running Example

As a running example for this section, we define the `name` predicate, which
takes some value as its first argument and unifies the second argument with a
"name" for this value, as a string.
```clojure
(fact
 (let-s [ps (->s (ok {})
                 (add-rule `(all [x y] (name x y ! "I don't know how to name" x)) {})
                 (add-rule `(all [] (name 1 "one")) {})
                 (add-rule `(all [] (name 2 "two")) {})
                 (add-rule `(all [y] (name 3 y ! "3 is not real")) {})
                 (add-rule `(all [] (name [] "empty vec")) {})
                 (add-rule `(all [x xs] (name [x & xs] "nonempty vec")) {})
                 (add-rule `(all [x] (name [x] "vec of length 1")) {})
                 (add-rule `(all [x y] (name [x y] "vec of length 2")) {}))]
        (do
          (def name-ps ps)
          (ok nil))) => {:ok nil})

```
This gives the values `1` and `2` their English names (`"one"` and `"two"`).
The value 3 will emit an error, stating some conspiracy theory against that
number. Vectors are named according to their size (empty, length 1 or 2 or
nonempty). Anything else will yield the explanation that it does not know how
to name it.

## Testing for Success

An assertion withing a `assert` block can be a simple goal. In such a case,
the expected behavior is for it to succeed.
```clojure
(fact
 (apply-assert-block name-ps `(assert (name 1 "one")
                                      (name 2 "two")) {} false) => {:ok name-ps})

```
However, if a goal expected to succeed fails, the explanation provided by the goal's
evaluation is returned.
```clojure
(fact
 (apply-assert-block name-ps `(assert (name 1 "one")
                                      (name 2 "two")
                                      (name 3 "three")
                                      (name [] "empty vec")) {} false) =>
 {:err ["3 is not real" "in assertion" `(name 3 "three")]})

```
## Testing for Failure

An assert block may also contain assertions that expect failure of goals.
identify invalid input and provide a correct explanation.

Expecting failure is done using the `!` symbol.
```clojure
(fact
 (apply-assert-block name-ps
                     `(assert (name 3 "three" ! "3 is not real")
                              (name 5 "five" ! "I don't know how to name" 5))
                     {} false) => {:ok name-ps})

```
The expression(s) after the `!` must match the error. For example, this
assertion will fail:
```clojure
(fact
 (apply-assert-block name-ps
                   `(assert (name 3 "three" ! "3 has no name")) {} false) =>
 {:err ["Wrong explanation is given:" ["3 is not real"] "instead of" ["3 has no name"]]})

```
If a goal is expected to fail but passes, the assertion fails too.
```clojure
(fact
 (apply-assert-block name-ps
                     `(assert (name 2 "two" ! "2 is not real")) {} false) =>
 {:err ["Expected failure for goal" `(name 2 "two") "but it succeeded"]})

```
## Recoverable and Unrecoverable Assertions

Error recovery is an important semantic analyzer feature. It allows for some
errors to be reported without stopping the analysis process, allowing for
more errors to be reported and for some semantic information to still be
available despite the program as a whole not being valid.

One example is a function that has some semantic error in its body. This
error should obviously be reported, but this should not hurt analysis of
code outside the function, including code that uses this function.

`apply-assert-block`'s fourth argument, the `recoverable` flag controls
its behavior with regards to whether it's recoverable or not.

By default, regardless of its value, an assertion block will return `:err` if
a condition fails. In the following we repeat an example from above, with
`recoverable` set to `true`, and see the same outcome.
```clojure
(fact
 (apply-assert-block name-ps `(assert (name 3 "three")) {} true) =>
 {:err ["3 is not real" "in assertion" `(name 3 "three")]})

```
However, if we provide an `*error-target*` binding, containing an atom with
an (initially empty) sequence, a recoverable call will return `:ok` and add
the error to the `*error-target*`.
```clojure
(fact
 (let [err-tgt (atom nil)]
   (binding [*error-target* err-tgt]
     (apply-assert-block name-ps `(assert (name 3 "three")) {} true)) =>
   {:ok name-ps}
   @err-tgt => [{:err ["3 is not real" "in assertion" `(name 3 "three")]}]))

```
A non-recoverable call will still return the `:err` status.
```clojure
(fact
 (let [err-tgt (atom nil)]
   (binding [*error-target* err-tgt]
     (apply-assert-block name-ps `(assert (name 3 "three")) {} false)) =>
   {:err ["3 is not real" "in assertion" `(name 3 "three")]}))

```
An `assert` block is recoverable, while `assert!` is unrecoverable, as can be
seen in the following examples, that use `apply-statement` which calls, among
other things, `apply-assert-block` based on the form symbol (`assert` or
`assert!`).
```clojure
(fact
 (let [err-tgt (atom nil)]
   (binding [*error-target* err-tgt]
     ;; Recoverable
     (apply-statement `(assert (name 3 "three")) name-ps {})) =>
   {:ok name-ps}
   @err-tgt => [{:err ["3 is not real" "in assertion" `(name 3 "three")]}])
 (let [err-tgt (atom nil)]
   (binding [*error-target* err-tgt]
     ;; Non-recoverable
     (apply-statement `(assert! (name 3 "three")) name-ps {})) =>
   {:err ["3 is not real" "in assertion" `(name 3 "three")]}))

```
### Skipping Recoverable Assertion Blocks

In the context of a language server, when loading a module that has
dependencies, it is often desired to skip checks in the dependency modules to
save time, as long as they are not essential for the correctness of the rules
generated by these dependencies, and only apply checks to the module that is
being loaded.

This can be done by binding `*skip-recoverable-assertions*` to `true` for
when analyzing the dependencies and to `false` when analyzing the main
module.
```clojure
(fact
 (binding [*skip-recoverable-assertions* true]
   (apply-statement `(assert (name 3 "three")) name-ps {}) => {:ok name-ps}
   (apply-statement `(assert! (name 3 "three")) name-ps {}) =>
   {:err ["3 is not real" "in assertion" `(name 3 "three")]}))
```

