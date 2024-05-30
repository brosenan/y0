(ns y0.predstore
  (:require [y0.core :refer [& specific-rule-without-base must-come-before conflicting-defs]]
            [y0.status :refer [let-s ok]]
            [clojure.string :refer [ends-with?]]))

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

(defn arg-key-generalizations [key]
  (cond
    (int? (:list key)) (lazy-seq (cons key (arg-key-generalizations (assoc key :list :non-empty))))
    (int? (:vec key)) (lazy-seq (cons key (arg-key-generalizations (assoc key :vec :non-empty))))
    (contains? key :symbol) (lazy-seq (cons key (arg-key-generalizations (dissoc key :symbol))))
    (contains? key :keyword) (lazy-seq (cons key (arg-key-generalizations (dissoc key :keyword))))
    (contains? key :value) (lazy-seq (cons key (arg-key-generalizations (dissoc key :value))))
    (= (:list key) :non-empty) (lazy-seq (cons key (arg-key-generalizations (dissoc key :list))))
    (= (:vec key) :non-empty) (lazy-seq (cons key (arg-key-generalizations (dissoc key :vec))))
    :else [key]))

(defn pd-check-base [pd head keys]
  (if (empty? keys)
    (if (-> head first str (ends-with? "?"))
      {:ok pd}
      {:err `(specific-rule-without-base ~head)})
    (let [[key & keys] keys]
      (if (contains? pd key)
        {:ok pd}
        (recur (assoc pd key {:overriden-by head}) head keys)))))

(defn- pd-check-generalizations [pd head keys]
  (if (empty? keys)
    {:ok pd}
    (pd-check-base pd head keys)))

(defn pd-store-rule [pd head body]
  (let-s [arg (ok head second)
          [key & keys] (ok (arg-key arg) arg-key-generalizations)
          pd (pd-check-generalizations pd head keys)]
         (if (contains? pd key)
           (let [existing (get pd key)]
             (cond
               (map? existing) {:err `(must-come-before ~head ~(:overriden-by existing))}
               (fn? existing) {:err `(conflicting-defs ~head ~(-> existing meta :head))}))
           (ok pd assoc key (with-meta body {:head head})))))

(defn pd-match [pd goal]
  (loop [keys (-> goal second arg-key arg-key-generalizations)]
    (let [[key & keys] keys]
      (cond (nil? key) nil
            (and (contains? pd key)
                 (fn? (get pd key))) (get pd key)
            :else (recur keys)))))
