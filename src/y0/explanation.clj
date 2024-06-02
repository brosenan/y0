(ns y0.explanation
  (:require [clojure.string :refer [join]]))

(declare explanation-expr-to-str)

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
    :else (pr-str expr)))

(defn explanation-to-str [why-not ps]
  (->> why-not
       (map #(if (string? %)
               %
               (explanation-expr-to-str % 3)))
       (join " ")))

(defn- has-location? [term]
  (and (instance? clojure.lang.Obj term)
         (let [m (meta term)]
           (and (contains? m :row)
                (contains? m :path)))))

(defn code-location [term]
  (cond
    (has-location? term) (meta term)
    (sequential? term) (->> term
                            (map code-location)
                            (filter #(not (nil? %)))
                            first)
    :else nil))
