(ns y0.explanation
  (:require [clojure.string :refer [join]]
            [y0.unify :refer [reify-term]]))

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
  (loop [expr (reify-term expr)
         budget budget]
    (cond
      (symbol? expr) (name expr)
      (seq? expr) (sequential-to-str budget expr "(" ")")
      (vector? expr) (sequential-to-str budget expr "[" "]")
      (instance? clojure.lang.Atom expr) (recur @expr budget)
      (nil? expr) "_"
      :else (pr-str expr))))

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
  (code-location' term))

(defn- project-location [loc]
  [(:row loc) (:path loc)])

(defn- unique-location [seen pairs]
  (if (empty? pairs)
    pairs
    (let [[[term loc] & pairs] pairs]
      (if (contains? seen (project-location loc))
        (recur seen pairs)
        (lazy-seq (cons [term loc] (-> seen (conj (project-location loc)) (unique-location pairs))))))))

(defn all-unique-locations [explanation]
  (->> explanation
       (map (fn [term] [term (code-location term)]))
       (filter #(-> % second nil? not))
       (unique-location #{})))