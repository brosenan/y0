(ns d0.compiler
  (:require [d0.core]
            [y0.rules :refer [new-vars satisfy-goal]]
            [y0.status :refer [let-s ok]]
            [y0.term-utils :refer [replace-vars]]
            [y0.unify :refer [reify-term]]))

(def ^:dynamic *bindings* {})

(declare compile-expr)

(defn- compile-let [d0-bindings expr tree src-ps d0-ps clj-bindings]
  (if (empty? d0-bindings)
    (let-s [expr' (compile-expr expr tree src-ps d0-ps)]
           (ok `(let ~(vec clj-bindings) ~expr')))
    (let [[var vexpr & others] d0-bindings
          var' (symbol (name var))]
      (let-s [vexpr' (compile-expr vexpr tree src-ps d0-ps)]
             (binding [*bindings* (assoc *bindings* var var')]
               (compile-let others expr tree src-ps d0-ps
                            (concat clj-bindings [var' vexpr'])))))))

(defmulti compile-form #(-> % first str) :default :default)

(defmethod compile-form :default [f]
  (constantly {:err ["Unsupported form" (first f)]}))

(defmethod compile-form "d0.core/let" [[_let bindings expr]]
  (fn [tree src-ps d0-ps]
    (compile-let bindings expr tree  src-ps d0-ps [])))

(defn- compile-expr [expr tree src-ps d0-ps]
  (cond
    (seq? expr) ((compile-form expr) tree src-ps d0-ps)
    (contains? *bindings* expr) (ok (get *bindings* expr))
    :else (ok expr)))

(defn compile [tree src-ps d0-ps]
  (let [vars (new-vars {} '[node params expr])
        goal (replace-vars '(d0.pred/root-expr "test" node params expr) vars)]
    (let-s [_ (satisfy-goal d0-ps goal nil)]
           (let [node (reify-term (get vars 'node))
                 params (reify-term (get vars 'params))
                 expr (reify-term (get vars 'expr))]
             (let-s [body (compile-expr expr tree src-ps d0-ps)]
                    (ok [`(defn ~'test []
                            ~body)]))))))
