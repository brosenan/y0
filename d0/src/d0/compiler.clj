(ns d0.compiler
  (:require [y0.rules :refer [new-vars satisfy-goal]]
            [y0.status :refer [let-s ok]]
            [y0.term-utils :refer [replace-vars]]
            [y0.unify :refer [reify-term]]))

(defn compile [tree src-ps d0-ps]
  (let [vars (new-vars {} '[node params expr])
        goal (replace-vars '(d0.pred/root-expr "test" node params expr) vars)]
    (let-s [_ (satisfy-goal d0-ps goal nil)]
           (let [node (reify-term (get vars 'node))
                 params (reify-term (get vars 'params))
                 expr (reify-term (get vars 'expr))]
             (ok [`(defn ~'test []
                     ~expr)])))))
