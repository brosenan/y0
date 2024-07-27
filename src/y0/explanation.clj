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
    (instance? clojure.lang.Atom expr) (recur @expr budget)
    (nil? expr) "_"
    :else (pr-str expr)))

(defn explanation-to-str [why-not]
  (->> why-not
       (map #(if (string? %)
               %
               (explanation-expr-to-str % 3)))
       (join " ")))

(defn- has-location? [term]
  (let [m (meta term)]
    (and (contains? m :row)
         (contains? m :path))))

(declare code-location)

(defn- code-location' [term]
  (cond
    (has-location? term) (meta term)
    (sequential? term) (->> term
                            (map code-location)
                            (filter #(not (nil? %)))
                            first)
    (instance? clojure.lang.Atom term) (code-location @term)
    :else nil))

(defn code-location [term]
  (let [res (code-location' term)]
    (println ">code-location" term (meta term) (has-location? term) res)
    res))