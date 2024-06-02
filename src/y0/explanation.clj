(ns y0.explanation
  (:require [clojure.string :refer [join]]))

(defn explanation-to-str [why-not ps]
  why-not)

(defn explanation-expr-to-str [expr budget]
  (cond
    (symbol? expr) (name expr)
    (seq? expr) (str "("
                     (->> expr
                          (take budget)
                          (map #(explanation-expr-to-str % 1))
                          (join " "))
                     (if (> (count expr) budget)
                       " ..."
                       "")
                     ")")
    :else (str expr)))