(ns y0.language-stylesheet
  (:require [clojure.string :as str]
            [clojure.set :as set]))

(defn- extract-matches [sel]
  (let [n (name sel)
        [n & matches] (str/split n #"[.]")]
    (if (symbol? sel)
      [(symbol (namespace sel) n) matches]
      [(keyword n) matches])))

(defn selector-to-func [sel]
  (let [[n matches] (extract-matches sel)]
    (fn [name pred-names]
      (and (= n name)
           (set/subset? matches pred-names)))))

(defn- compile-rules [rules]
  (loop [rules rules
         crules nil]
    (if (empty? rules)
      crules
      (let [[sel attrs & rules] rules]
        (recur rules (conj crules [(selector-to-func sel) attrs]))))))

(defn compile-stylesheet [lss]
  (let [[default & rules] lss
        rules (compile-rules rules)]
    (fn [node-name matched-preds attr]
      (loop [rules rules]
        (if (empty? rules)
          (get default attr)
          (let [[[sel attrs] & rules] rules]
            (if (and (contains? attrs attr)
                     (sel node-name matched-preds))
              (get attrs attr)
              (recur rules))))))))
