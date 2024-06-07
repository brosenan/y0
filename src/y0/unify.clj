(ns y0.unify
  (:require [y0.core :refer [&]]
            [clojure.walk :as walk]))

(declare unify)

(defn- tail? [a]
  (and (= (count a) 2)
         (= (first a) 'y0.core/&)))

(defn- unify-all [a b constructor]
  (cond
    (tail? a) (unify (second a) (constructor b))
    (tail? b) (unify (second b) (constructor a))
    (empty? a) (empty? b)
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
        (tail? ts) (reify-term (second ts))
        :else (let [[e & es] ts]
                (cons (reify-term e)
                      (reify-terms es)))))

(defn reify-term [t]
  (cond
    (instance? clojure.lang.Atom t) (resolve-var t)
    (vector? t) (vec (reify-terms t))
    (seq? t) (seq (reify-terms t))
    :else t))