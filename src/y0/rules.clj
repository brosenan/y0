(ns y0.rules
  (:require [y0.status :refer [->s ok]]
            [y0.predstore :refer [store-rule]]
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
        head (postwalk-replace vars head)]
    (->s (ok ps)
         (store-rule head (constantly 42)))))