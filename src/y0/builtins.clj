(ns y0.builtins 
  (:require [y0.unify :refer [unify]]))

(defn inspect [goal why-not ps]
  (let [[_inspect term kind] goal
        inspected (cond
                    (int? term) :int
                    (double? term) :float
                    (string? term) :string
                    (symbol? term) :symbol
                    (keyword? term) :keyword
                    (vector? term) :vector
                    (seq? term) :list
                    (set? term) :set
                    (map? term) :map
                    :else :unknown)]
    (if (unify kind inspected)
      {:ok nil}
      {:err why-not})))

(defn add-builtin [ps name arity func]
  (assoc ps {:name (str "y0.core/" name) :arity arity} {{} func}))

(defn add-builtins [ps]
  (-> ps
      (add-builtin "inspect" 2 inspect)))