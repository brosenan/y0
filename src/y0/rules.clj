(ns y0.rules
  (:require [y0.status :refer [->s ok let-s]]
            [y0.predstore :refer [store-rule match-rule]]
            [y0.unify :refer [unify]]
            [clojure.walk :refer [postwalk-replace]]))

(defn new-vars [bindings symbols]
  (loop [bindings bindings
         [sym & syms] symbols]
    (if (nil? sym)
      bindings
      (-> bindings 
          (assoc sym (atom nil))
          (recur syms)))))

(defn add-rule [ps rule]
  (let [[_all bindings head] rule
        vars (new-vars {} bindings)
        head' (postwalk-replace vars head)]
    (->s (ok ps)
         (store-rule head' (fn [goal why-not]
                            (let [vars (new-vars {} bindings)
                                  head (postwalk-replace vars head)]
                              (if (unify goal head)
                                {:ok nil}
                                {:err why-not})))))))

(defn satisfy-goal [ps goal why-not]
  (let-s [rule (match-rule ps goal)]
         (rule goal why-not)))