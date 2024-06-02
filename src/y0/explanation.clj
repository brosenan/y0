(ns y0.explanation
  (:require [clojure.string :refer [join]]))

(defn explanation-to-str [why-not ps]
  why-not)

(defn- sequential-to-str [budget expr opener closer]
  (str opener
       (->> expr
            (take budget)
            (map #(explanation-expr-to-str % 1))
            (join " "))
       (if (> (count expr) budget)
         " ..."
         "")
       closer))

(defn explanation-expr-to-str [expr budget]
  (cond
    (symbol? expr) (name expr)
    (seq? expr) (sequential-to-str budget expr "(" ")")
    (vector? expr) (sequential-to-str budget expr "[" "]")
    :else (str expr)))