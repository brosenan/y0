(ns y0.explanation
  (:require [clojure.string :refer [join]]
            [clojure.java.io :as io]
            [y0.unify :refer [reify-term]]
            [y0.location-util :refer [extract-location]]))

(declare explanation-expr-to-str)

(def ^:dynamic *stringify-expr* #(explanation-expr-to-str % 3))

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
               (*stringify-expr* %)))
       (join " ")))

(defn- has-location? [term]
  (let [m (meta term)]
    (and (contains? m :start)
         (contains? m :end)
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

(defn- loc-contained? [[_ a] [_ b]]
  (and (= (:path a) (:path b))
       (or (and (>= (:start a) (:start b))
                (< (:end a) (:end b)))
           (and (> (:start a) (:start b))
                (<= (:end a) (:end b))))))

(defn- unique-location [pairs]
  (filter (fn [x] (nil? (some #(loc-contained? x %) pairs))) pairs))

(defn all-unique-locations [explanation]
  (->> explanation
       (map (fn [term] [term (code-location term)]))
       (filter #(-> % second nil? not))
       unique-location))

(defn stringify-lines [lines]
  (if (> (count lines) 2)
    (str (first lines) " ... " (last lines))
    (join " " lines)))

(defn expr-to-str [expr]
  (if (has-location? expr)
    (let [loc (meta expr)]
      (with-open [r (io/reader (:path loc))]
        (-> (line-seq r)
            (extract-location loc)
            stringify-lines)))
    (explanation-expr-to-str expr 3)))