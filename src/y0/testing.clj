(ns y0.testing
  (:require [y0.status :refer [->s ok let-s]]
            [y0.rules :refer [satisfy-goal]]
            [y0.predstore :refer [store-rule match-rule]]
            [y0.unify :refer [unify]]
            [y0.core :refer [!]]
            [clojure.walk :refer [postwalk-replace]]))

(defn apply-test-block [ps test-block]
  (let [[_test & tests] test-block
        [test & tests] tests]
    (satisfy-goal ps test "Test failed without explanation")))