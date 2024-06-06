(ns y0.testing
  (:require [y0.status :refer [->s ok let-s]]
            [y0.rules :refer [satisfy-goal split-goal]]
            [y0.predstore :refer [store-rule match-rule]]
            [y0.unify :refer [unify]]
            [y0.core :refer [!]]
            [clojure.walk :refer [postwalk-replace]]))

(defn- expect-status [status why-not test]
  (if (nil? why-not)
    (cond
      (contains? status :err) {:err (concat (:err status) ["in test" test])}
      :else status)
    (cond
      (contains? status :ok) {:err 
                              ["Expected failure for goal" test "but it succeeded"]}
      (not (unify (:err status) why-not)) {:err
                                           ["Wrong explanation is given:" (:err status) "instead of" why-not]}
      :else (ok nil))))

(defn apply-test-block [ps test-block]
  (let [[_test & tests] test-block]
    (loop [tests tests]
      (let [[test & tests] tests]
        (if (nil? test)
          (ok ps)
          (let-s [[test why-not] (ok  test split-goal nil)
                  _nil (expect-status 
                        (satisfy-goal ps test ["Test failed without explanation"]) why-not test)]
                 (recur tests)))))))