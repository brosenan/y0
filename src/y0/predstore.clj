(ns y0.predstore
  (:require [y0.unify :refer [&]]))

(defn pred-key [[name-sym & args]]
  {:name (str name-sym)
   :arity (count args)})

(defn- has-tail? [l]
  (cond
    (< (count l) 2) false
    (> (count l) 2) (recur (rest l))
    (= (first l) &) (instance? clojure.lang.Atom (second l))
    :else false))

(declare arg-key)

(defn- sequential-key [arg attr]
  (if (empty? arg)
    {attr :empty}
    (let [first-elem-key (arg-key (first arg))]
      (merge first-elem-key
             (if (has-tail? arg)
               {attr :non-empty}
               {attr (count arg)})))))

(defn arg-key [arg]
  (cond
    (symbol? arg) {:symbol (str arg)}
    (keyword? arg) {:keyword (str arg)}
    (instance? clojure.lang.Atom arg) {}
    (seq? arg) (sequential-key arg :list)
    (vector? arg) (sequential-key arg :vec)
    :else {:value arg}))