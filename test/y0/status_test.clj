(ns y0.status-test
  (:require [midje.sweet :refer [fact => throws]]
            [y0.status :refer [unwrap-status ok ->s update-with-status]]
            [y0.core :refer [on-key]]))

;; One key aspect of y0 is the fact that evaluation can result in either a value or an explanation _why not_.
;; In Clojure, we could represent this using return values and exceptions respectively. However, exceptions
;; are limited by the constraints of the JVM's handling of exceptions.

;; Instead of using exceptions, we prefer using _status types_, similar to the `Result<T>` type in Rust.
;; Status values are maps that hold a single entry. Either `{:ok (my value)}` or `{:err (some explanation)}`.

;; ## Unwrapping Statuses

;; The function `unwrap-status` takes a status and either returns the value or throws an exception.
(fact
 (unwrap-status {:ok 42}) => 42
 (unwrap-status {:err '(this is an explanation)}) => (throws "(this is an explanation)"))

;; ## Status-Returning Functions

;; A _status-returning function_ is, as the name suggests, a function that returns a status.
;; For example, the following function returns a status:
(defn safe-divide [x y]
  (if (= y 0)
    {:err (list 'cannot 'divide x 'by 0)}
    {:ok (/ x y)}))

;; Now, dividing by nonzero numbers succeeds, while dividing by zero returns an explanation.
(fact
 (safe-divide 6 3) => {:ok 2}
 (safe-divide 6 0) => {:err '(cannot divide 6 by 0)})

;; The macro `ok` can be used to turn a regular function into a status-returning function.
(fact
 (ok 2 + 3) => {:ok 5})

;; Now you may have noticed something strange. `ok`'s first argument is not the function (or macro) to be
;; called, but rather the first argument. Only the second argument to `ok` is the function, followed by the
;; rest of the arguments.

;; The reason for this order will become clear soon.

;; ## Threading Statuses

;; The term _threading_ in Clojure applies to the use of macros such as `->` or `->>`, which take a sequence
;; of s-expressions and evaluates them in sequence, providing the result as the first (`->`) or last (`->>`)
;; argument of the next expression.

;; We define the macro `->s` as a threading macro for status-returning functions.

;; Given just one expression, it simply evaluates it.
(fact
 (->s (ok 42 identity)) => {:ok 42})

;; Given two or more expressions, it threads the `:ok` value as the first argument of each subsequent expression.
(fact
 (->s (ok 42 identity)
      (safe-divide 6)) => {:ok 7}
 (->s (ok 42 identity)
      (ok - 20)
      (safe-divide 2)) => {:ok 11})

;; In the last example we can see the reason why `ok` takes the first argument before the function name. This 
;; allowed us to thread `(ok - 20)`.

;; Let us now define the function `safe-divide-rev`, which is similar to `safe-divide`, but takes the denominator
;; as its first argument.
(defn safe-divide-rev [y x]
  (safe-divide x y))

;; With this function, the first argument (threaded by `->s`) determines success or failure.

;; `->s` fails on the first `:err`, as can be seen here:
(fact
 (->s (ok 2 identity)
      (safe-divide-rev 6) ;; => 3
      (ok - 3) ;; => 0
      (safe-divide-rev 4) ;; error!
      (safe-divide-rev 3)) => {:err '(cannot divide 4 by 0)})

;; Updating with Statuses

;; `update-with-status` is similar to Clojure's `update`. It takes a map, a key and a function
;; as arguments and applies the function to the value corresponding to the given key, returning
;; the map with the value replaced. However, in the case of `update-with-status`, the function
;; is a status-returning function. For `:ok`, it will replace the value just like `update`.
(fact
 (let [m {:a 4 :b 5}]
   (update-with-status m :a #(safe-divide % 2)) => {:ok {:a 2 :b 5}}))

;; If the function returns `:err`, the error is propagagated, specifying the key for context.
(fact
 (let [m {:a 4 :b 5}]
   (update-with-status m :a #(safe-divide % 0)) => 
   {:err '(on-key (cannot divide 4 by 0) :a)}))
