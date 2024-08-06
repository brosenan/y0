(ns y0.unify
  (:require [y0.core :refer [&]]
            [clojure.walk :as walk]))

(declare unify reify-term)

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
      (instance? clojure.lang.Atom a) (do (reset! a (reify-term b))
                                          true)
      (instance? clojure.lang.Atom b) (do (reset! b (reify-term a))
                                          true)
      (seq? a) (and (seq? b)
                    (unify-all a b sequence))

      (vector? a) (and (vector? b)
                       (unify-all a b vec))
      :else (= a b))))

(defn- valid-tail? [tail]
  (cond
    (sequential? tail) true
    (instance? clojure.lang.Atom tail) (recur @tail)
    :else false))

(defn- free-var? [tail]
  (and (instance? clojure.lang.Atom tail)
       (nil? @tail)))

(defn- reify-terms [ts]
  (cond (empty? ts) ts
        (tail? ts) (let [tail (second ts)]
                     (if (valid-tail? tail)
                       (reify-term tail)
                       ts))
        :else (let [[e & es] ts]
                (cons (reify-term e)
                      (reify-terms es)))))

(defn reify-term [t]
  (cond
    (instance? clojure.lang.Atom t) (resolve-var t)
    (vector? t) (with-meta (vec (reify-terms t)) (meta t))
    (seq? t) (if (empty? t)
               t
               (with-meta (seq (reify-terms t)) (meta t)))
    :else t))