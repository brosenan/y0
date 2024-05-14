(ns y0.unify
  (:require [clojure.walk :as walk]))

(def & '&)
(declare unify)

(defn- unify-all [a b constructor]
  (cond
    (empty? a) (empty? b)
    (and (= (count a) 2)
         (= (first a) '&)) (unify (second a) (constructor b))
    :else (let [[a & as] a
                [b & bs] b]
            (and (unify a b)
                 (recur as bs constructor)))))

(defn- resolve-var [a]
  (cond
    (not (instance? clojure.lang.Atom a)) a
    (nil? @a) a
    :else (recur @a)))

(defn unify [a b]
  (let [a (resolve-var a)
        b (resolve-var b)]
    (cond
      (instance? clojure.lang.Atom a) (do (reset! a b)
                                          true)
      (instance? clojure.lang.Atom b) (do (reset! b a)
                                          true)
      (seq? a) (and (seq? b)
                    (unify-all a b sequence))

      (vector? a) (and (vector? b)
                       (unify-all a b vec))
      :else (= a b))))

(declare reify-term)

(defn- reify-terms [ts]
  (cond (empty? ts) ts
        (and (= (count ts) 2)
             (= (first ts) '&)) (reify-term (second ts))
        :else (let [[e & es] ts]
                (cons (reify-term e)
                      (reify-terms es)))))

(defn reify-term [t]
  (cond
    (instance? clojure.lang.Atom t) (resolve-var t)
    (vector? t) (vec (reify-terms t))
    (seq? t) (seq (reify-terms t))
    :else t))