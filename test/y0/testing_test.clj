(ns y0.testing-test
  (:require [midje.sweet :refer [fact =>]]
            [y0.rules :refer [apply-test-block add-rule]]
            [y0.core :refer [all test !]]
            [y0.status :refer [->s ok let-s]]))

;; Testing is an important aspect of any programming environment, and it is for this
;; reason that we bake testing into $y_0$, rather than leaving it as an afterthought.

;; $y_0$ testing is based on _test blocks_, or the form `(test tests...)`. Each test
;; in `tests` is a goal, expecting to either succeed or fail. In case of failure,
;; an expected explanation is provided using the `!` symbol.

;; The function `apply-test-block` takes a predstore and a test-block and returns
;; the given predstore if all tests have passed, or an appropriate explanation if
;; something failed.

;; ## Running Example

;; As a running example for this section, we define the `name` predicate, which takes
;; some value as its first argument and unifies the second argument with a "name" for
;; this value, as a string.
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
;; This gives the values `1` and `2` their English names (`"one"` and `"two"`).
;; The value 3 will emit an error, stating some conspiracy theory against that number.
;; Vectors are named according to their size (empty, length 1 or 2 or nonempty).
;; Anything else will yield the explanation that it does not know how to name it.

;; ## Testing for Success

;; A test withing a `test` block can be a simple goal. In such a case, the expected
;; behavior is for it to succeed.
(fact
 (apply-test-block name-ps `(test (name 1 "one")
                                  (name 2 "two"))) => {:ok name-ps})

;; However, if a goal expected to succeed fails, the explanation provided by the goal's
;; evaluation is returned.
(fact
 (apply-test-block name-ps `(test (name 1 "one")
                                  (name 2 "two")
                                  (name 3 "three")
                                  (name [] "empty vec"))) => {:err ["3 is not real" "in test" `(name 3 "three")]})

;; ## Testing for Failure

;; A test block may also contain tests that expect failure of goals. This is important
;; in order to make sure the $y_0$ program can correctly identify invalid input and
;; provide a correct explanation.

;; Expecting failure is done using the `!` symbol.
(fact
 (apply-test-block name-ps 
                   `(test (name 3 "three" ! "3 is not real")
                          (name 5 "five" ! "I don't know how to name" 5))) => {:ok name-ps})

;; The expression(s) after the `!` must match the error. For example, this test will fail:
(fact
 (apply-test-block name-ps
                   `(test (name 3 "three" ! "3 has no name"))) =>
 {:err ["Wrong explanation is given:" ["3 is not real"] "instead of" ["3 has no name"]]})

;; If a goal is expected to fail but passes, the test fails too.
(fact
 (apply-test-block name-ps
                   `(test (name 2 "two" ! "2 is not real"))) =>
 {:err ["Expected failure for goal" `(name 2 "two") "but it succeeded"]})