(ns y0.status
  (:require [y0.core :refer [on-key]]))

(defn unwrap-status [{:keys [ok err]}]
  (if (nil? err)
    ok
    (throw (Exception. (str err)))))

(defmacro ok [arg f & args]
  {:ok `(~f ~arg ~@args)})

(defmacro ->s [expr & exprs]
  (if (empty? exprs)
    expr
    (let [val (gensym "val")]
      `(let [~val ~expr]
         ~(let [[next-expr & exprs] exprs
                [func & args] next-expr]
            `(if (nil? (:err ~val))
               (->s (~func (:ok ~val) ~@args)
                    ~@exprs)
               ~val))))
    
    ))

(defn update-with-status [m k f]
  (let [v (get m k)
        {:keys [ok err]} (f v)]
    (if (nil? err)
      {:ok (assoc m k ok)}
      {:err (list on-key err k)})))
