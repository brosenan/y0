(ns y0.rules
  (:require [y0.status :refer [->s ok let-s]]
            [y0.predstore :refer [store-rule match-rule]]
            [y0.unify :refer [unify]]
            [y0.core :refer [!]]
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

(defn add-rule [ps rule]
  (let [[_all bindings head] rule
        [head why-not] (split-goal head nil)
        vars (new-vars {} bindings)
        head' (postwalk-replace vars head)
        body (if why-not
               (fn [goal _why-not _ps]
                 (let [why-not (postwalk-replace vars why-not)
                       head (postwalk-replace vars head)]
                   (unify goal head)
                   {:err why-not}))
               (fn [goal why-not ps]
                 (let [vars (new-vars {} bindings)
                       head (postwalk-replace vars head)]
                   (if (unify goal head)
                     {:ok nil}
                     {:err why-not}))))]
    (store-rule ps head' body)))

(defn satisfy-goal [ps goal why-not]
  (let [[goal why-not] (split-goal goal why-not)]
    (let-s [rule (match-rule ps goal)]
           (rule goal why-not ps))))
