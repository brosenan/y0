(ns y0.rules
  (:require [y0.status :refer [->s ok let-s]]
            [y0.predstore :refer [store-rule match-rule store-translation-rule get-rules-to-match]]
            [y0.unify :refer [unify]]
            [y0.core :refer [! exist <- =>]]
            [clojure.walk :refer [postwalk-replace]]))

(defn new-vars [bindings symbols]
  (loop [bindings bindings
         [sym & syms] symbols]
    (if (nil? sym)
      bindings
      (-> bindings 
          (assoc sym (atom nil))
          (recur syms)))))

(defn split-goal [goal why-not]
  (let [bang-index (.indexOf goal `!)] 
    (if (= bang-index -1)
      [goal why-not]
      [(take bang-index goal) (-> bang-index inc (drop goal) vec)])))

(declare satisfy-goal check-conditions add-rule)

(defn- check-condition [condition ps vars]
  (cond
    (-> condition first (= `exist)) (let [[_exist bindings & conditions] condition
                                          vars (merge vars (new-vars {} bindings))]
                                      (check-conditions conditions ps vars))
    :else (let [condition (postwalk-replace vars condition)]
            (satisfy-goal ps condition nil))))

(defn- check-conditions [conditions ps vars]
  (if (empty? conditions)
    (ok nil)
    (let-s [[condition & conditions] (ok conditions)
            _ (check-condition condition ps vars)]
           (recur conditions ps vars))))

(defn- add-deduction-rule [ps bindings head conditions vars]
  (let [[head why-not] (split-goal head nil)
        vars' (new-vars vars bindings)
        head' (postwalk-replace vars' head)
        body (if why-not
               (fn [goal _why-not _ps]
                 (let [vars (new-vars vars bindings)
                       why-not (postwalk-replace vars why-not)
                       head (postwalk-replace vars head)]
                   (unify goal head)
                   {:err why-not}))
               (fn [goal why-not ps]
                 (let [vars (new-vars vars bindings)
                       head (postwalk-replace vars head)]
                   (if (unify goal head)
                     (check-conditions conditions ps vars)
                     {:err why-not}))))]
    (store-rule ps head' body)))

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

(defn- apply-statement [statement ps vars]
  (let [[form & _] statement]
    (case form
      y0.core/all (add-rule ps statement vars)
      y0.core/test (apply-test-block ps statement)
      (loop [trans-rules (seq (get-rules-to-match ps statement))
             ps ps]
        (if (empty? trans-rules)
          (ok ps)
          (let-s [[rule & rules] (ok trans-rules)
                  ps (rule statement ps)]
            (recur rules ps)))))))

(defn apply-statements [statements ps vars]
  (loop [statements statements
         ps ps]
    (if (empty? statements)
      (ok ps)
      (let [[statement & statements] statements]
        (let-s [ps (apply-statement statement ps vars)]
               (recur statements ps))))))

(defn- add-translation-rule [ps bindings head terms]
  (let [vars (new-vars {} bindings)
        head' (postwalk-replace vars head)]
    (store-translation-rule ps head'
                            (fn [statement ps]
                              (let [vars (new-vars {} bindings)
                                    head (postwalk-replace vars head)]
                                (if (unify statement head)
                                  (apply-statements terms ps vars)
                                  (ok ps)))))))

(defn add-rule [ps rule vars]
  (let [[_all bindings head op & terms] rule]
    (cond
      (or (nil? op) (= op `<-)) (add-deduction-rule ps bindings head terms vars)
      (= op `=>) (add-translation-rule ps bindings head terms))))

(defn satisfy-goal [ps goal why-not]
  (let [[goal why-not] (split-goal goal why-not)]
    (let-s [rule (match-rule ps goal)]
           (rule goal why-not ps))))
