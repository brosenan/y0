(ns y0.instaparser
  (:require [instaparse.core :as insta]
            [clojure.string :as str]
            [y0.term-utils :refer [postwalk-meta]]))

(def layout-separator "--layout--")

(defn- parser-with-line-nums [parser]
  (fn [text]
    (insta/add-line-and-column-info-to-metadata text (parser text))))

(defn instaparse-grammar [grammar]
  (if (str/includes? grammar layout-separator)
    (let [index (str/index-of grammar layout-separator)
          layout-grammar (subs grammar (+ index (count layout-separator)))
          layout-parser (insta/parser layout-grammar)
          grammar (subs grammar 0 index)]
      (parser-with-line-nums (insta/parser grammar :auto-whitespace layout-parser)))
    (parser-with-line-nums (insta/parser grammar))))

(defn add-locations [tree path]
  (postwalk-meta (fn [loc]
                   {:path path
                    :start (+ (* (get loc :instaparse.gll/start-line 0) 1000000) (get loc :instaparse.gll/start-column 0))
                    :end (+ (* (get loc :instaparse.gll/end-line 0) 1000000) (get loc :instaparse.gll/end-column 0))}) tree))

(defn add-namespace [node ns identifier-keywords]
  (let [[kw name & others] node]
    (cond
      (not (contains? identifier-keywords kw)) node
      (not (empty? others)) (throw (Exception. (str kw " node should have one element but has " (-> others count inc))))
      (not (string? name)) (throw (Exception. (str kw " node should contain a single string. Found: " name)))
      :else [kw name ns])))

(defn deps-extractor [coll-atom kw]
  (fn [node]
    (let [[kw' module & others] node]
      (when (= kw' kw)
        (cond
          (not (empty? others)) (throw (Exception. (str kw " node should contain one element but has " (-> others count inc))))
          (not (string? module)) (throw (Exception. (str kw " node should contain a single string. Found: " module))))
        (swap! coll-atom conj module)))
    node))