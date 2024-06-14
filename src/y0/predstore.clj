(ns y0.predstore
  (:require [y0.core :refer [& specific-rule-without-base must-come-before conflicting-defs undefined-predicate]]
            [y0.status :refer [let-s ok ->s update-with-status]]
            [clojure.string :refer [ends-with?]]
            [clojure.set :refer [union]]))

(defn pred-key [[name-sym & args]]
  {:name (str name-sym)
   :arity (count args)})

(defn- free-var? [v]
  (and (instance? clojure.lang.Atom v)
       (nil? @v)))

(defn- has-tail? [l]
  (cond
    (< (count l) 2) false
    (> (count l) 2) (recur (rest l))
    (= (first l) 'y0.core/&) (free-var? (second l))
    :else false))

(declare arg-key)

(defn- sequential-key [arg attr]
  (let [seq-key (if (has-tail? arg)
                  {attr :any}
                  {attr (count arg)})]
    (if (nil? (first arg))
      seq-key
      (merge seq-key (arg-key (first arg))))))

(defn arg-key [arg]
  (cond
    (symbol? arg) {:symbol (str arg)}
    (keyword? arg) {:keyword (str arg)}
    (free-var? arg) {}
    (instance? clojure.lang.Atom arg) (recur @arg)
    (seq? arg) (sequential-key arg :list)
    (vector? arg) (sequential-key arg :vec)
    :else {:value arg}))

(defn generalize-arg [key]
  (let [keys []
        keys (if (and (= (:list key) :any) (= (count key) 1)) (conj keys (dissoc key :list)) keys)
        keys (if (and (= (:vec key) :any) (= (count key) 1)) (conj keys (dissoc key :vec)) keys)
        keys (if (contains? key :symbol) (conj keys (dissoc key :symbol)) keys)
        keys (if (contains? key :keyword) (conj keys (dissoc key :keyword)) keys)
        keys (if (contains? key :value) (conj keys (dissoc key :value)) keys)
        keys (if (int? (:list key)) (conj keys (assoc key :list :any)) keys)
        keys (if (int? (:vec key)) (conj keys (assoc key :vec :any)) keys)]
    keys))

(defn- arg-keys-generalizations [keys used]
  (if (empty? keys)
    keys
    (lazy-seq (concat keys (arg-keys-generalizations (->> keys
                                                          (mapcat generalize-arg)) (union used keys))))))

(defn arg-key-generalizations [key]
  (distinct (arg-keys-generalizations [key] #{})))

(defn pd-check-base [pd head keys]
  (if (empty? keys)
    (if (-> head first str (ends-with? "?"))
      {:ok pd}
      {:err ["A specific rule for" head
             "is defined without first defining a base rule for the predicate with a free variable as its first argument"]})
    (let [[key & keys] keys]
      (if (contains? pd key)
        {:ok pd}
        (recur (assoc pd key {:overriden-by head}) head keys)))))

(defn- pd-check-generalizations [pd head keys]
  (if (empty? keys)
    {:ok pd}
    (pd-check-base pd head keys)))

(defn- check-exclusion-marker [pd marker arg]
  (if (contains? pd marker)
    (let [other (get pd marker)]
      {:err ["A rule with the pattern" arg
             "cannot coexist with a rule with the pattern" other
             "within the same predicate due to ambiguous generalizations"]})))

(defn- pd-update-tags [pd arg]
  (cond (seq? arg) (cond
                     (free-var? (first arg)) (->s
                                              (ok pd)
                                              (check-exclusion-marker :variadic-form-list arg)
                                              (ok assoc :fixed-size-list arg))
                     (has-tail? arg) (ok pd assoc :variadic-form-list arg)
                     :else (ok pd))
        (vector? arg) (cond
                        (free-var? (first arg)) (ok pd assoc :fixed-size-vec arg)
                        (has-tail? arg) (ok pd assoc :variadic-form-vec arg)
                        :else (ok pd))
        :else (ok pd)))

(defn pd-store-rule [pd head body]
  (let-s [arg (ok head second)
          [key & keys] (ok (arg-key arg) arg-key-generalizations)
          pd (pd-check-generalizations pd head keys)]
         (if (contains? pd key)
           (let [existing (get pd key)]
             (cond
               (map? existing) {:err ["Rule" head "must be defined before rule"
                                      (:overriden-by existing) "because it is more generic"]}
               (fn? existing) {:err ["The rule for" head
                                     "conflicts with a previous rule defining"
                                     (-> existing meta :head)]}))
           (->s 
            (ok pd)
            (pd-update-tags arg)
            (ok assoc key (with-meta body {:head head}))))))

(defn pd-match [pd goal]
  (loop [keys (-> goal second arg-key arg-key-generalizations)]
    (let [[key & keys] keys]
      (cond (nil? key) nil
            (and (contains? pd key)
                 (fn? (get pd key))) (get pd key)
            :else (recur keys)))))

(defn store-rule [ps head body]
  (update-with-status ps (pred-key head) #(pd-store-rule % head body)
                      (fn [err {:keys [name arity]}]
                        (concat err ["in predicate" name "with arity" arity]))))

(defn- get-or-err [m k err]
  (let [v (get m k)]
    (if (nil? v)
      {:err err}
      (ok v))))

(defn match-rule [ps goal]
  (->s (ok ps)
       (get-or-err (pred-key goal) 
                   ["Undefined predicate" (first goal) 
                    "with arity" (-> goal count dec)])
       (ok pd-match goal)))
