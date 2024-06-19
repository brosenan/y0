(ns y0.rules
  (:require [y0.status :refer [->s ok let-s]]
            [y0.predstore :refer [store-rule match-rule]]
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

(declare satisfy-goal check-conditions)

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

(defn- add-deduction-rule [ps bindings head conditions]
  (let [[head why-not] (split-goal head nil)
        vars (new-vars {} bindings)
        head' (postwalk-replace vars head)
        body (if why-not
               (fn [goal _why-not _ps]
                 (let [vars (new-vars {} bindings)
                       why-not (postwalk-replace vars why-not)
                       head (postwalk-replace vars head)]
                   (unify goal head)
                   {:err why-not}))
               (fn [goal why-not ps]
                 (let [vars (new-vars {} bindings)
                       head (postwalk-replace vars head)]
                   (if (unify goal head)
                     (check-conditions conditions ps vars)
                     {:err why-not}))))]
    (store-rule ps head' body)))

(defn- add-translation-rule [ps bindings head terms]
  (let [vars (new-vars {} bindings)]
    `ps))

(defn add-rule [ps rule]
  (let [[_all bindings head op & terms] rule]
    (cond
      (or (nil? op) (= op `<-)) (add-deduction-rule ps bindings head terms)
      (= op `=>) (add-translation-rule ps bindings head terms))))

(defn satisfy-goal [ps goal why-not]
  (let [[goal why-not] (split-goal goal why-not)]
    (let-s [rule (match-rule ps goal)]
           (rule goal why-not ps))))
