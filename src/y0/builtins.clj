(ns y0.builtins 
  (:require [y0.unify :refer [unify]]))

(defn- unify-or-err [a b why-not]
  (if (unify a b)
      {:ok nil}
      {:err why-not}))

(defn eq [goal why-not _ps]
  (let [[_= a b] goal]
    (unify-or-err a b why-not)))

(defn kind-of [term]
  (cond
    (instance? clojure.lang.Atom term) (recur @term)
    (int? term) :int
    (double? term) :float
    (string? term) :string
    (symbol? term) :symbol
    (keyword? term) :keyword
    (vector? term) :vector
    (seq? term) :list
    (set? term) :set
    (map? term) :map
    :else :unknown))

(defn inspect [goal why-not _ps]
  (let [[_inspect term kind] goal
        inspected (kind-of term)]
    (unify-or-err kind inspected why-not)))

(defn add-builtin [ps name arity func]
  (assoc ps {:name (str "y0.core/" name) :arity arity} {{} func}))

(defn add-builtins [ps]
  (-> ps
      (add-builtin "=" 2 eq)
      (add-builtin "inspect" 2 inspect)))