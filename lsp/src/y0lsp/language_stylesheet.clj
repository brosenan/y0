(ns y0lsp.language-stylesheet
  (:require [y0.explanation :refer [explanation-to-str]]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.walk :as walk]))

(defn- extract-matches [sel]
  (let [n (name sel)
        [n & matches] (str/split n #"[.]")
        n (cond
            (empty? n) nil
            (symbol? sel) (symbol (namespace sel) n)
            (keyword sel) (keyword n))]
    [n matches]))

(defn- match-name [a b]
  (cond
    (nil? a) true
    (= a b) true
    (and (symbol? a)
         (symbol? b)) (= (name a) (name b))
    :else false))

(defn selector-to-func [sel]
  (let [[n matches] (extract-matches sel)]
    (fn [nm pred-names]
      (and (match-name n nm)
           (set/subset? matches pred-names)))))

(defn- compile-rules [rules]
  (loop [rules rules
         crules nil]
    (if (empty? rules)
      crules
      (let [[sel attrs & rules] rules]
        (recur rules (conj crules [(selector-to-func sel) attrs]))))))

(defn- get-pred [map pred]
  (->> (for [[key v] map
             :when (match-name pred key)]
         v)
       first))

(defn- handle-with-pred [[_with-pred [pred & params] body] node]
  (let [args (-> node meta :matches deref (get-pred pred) :args)
        rep (zipmap params args)]
    (walk/postwalk-replace rep body)))

(defn- handle-with-node [[_with-node params body] [_head & args]]
  (let [rep (zipmap params args)]
    (walk/postwalk-replace rep body)))

(declare eval-attr)

(defn- handle-str [[_str v] node]
  (explanation-to-str v))

(defn- maybe-form [expr node]
  (let [name (first expr)]
    (cond
      (= name 'with-pred) (handle-with-pred expr node)
      (= name 'with-node) (handle-with-node expr node)
      (= name 'str) (handle-str expr node)
      :else expr)))

(defn eval-attr [expr node]
  (walk/postwalk (fn [expr]
                  (if (seq? expr)
                    (maybe-form expr node)
                    expr))
                expr))

(defn node-name [node]
  (cond
    (sequential? node) (first node)
    :else node))

(defn compile-stylesheet [lss]
  (let [[default & rules] lss
        rules (compile-rules rules)]
    (fn [node attr]
      (let [node-name (node-name node)
            matched-preds (->> node meta :matches deref keys (map name) set)
            attr-expr (loop [rules rules]
                        (if (empty? rules)
                          (get default attr)
                          (let [[[sel attrs] & rules] rules]
                            (if (and (contains? attrs attr)
                                     (sel node-name matched-preds))
                              (get attrs attr)
                              (recur rules)))))]
        (eval-attr attr-expr node)))))
