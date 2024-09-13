(ns y0.instaparser
  (:require [instaparse.core :as insta]
            [clojure.string :as str]
            [y0.term-utils :refer [postwalk-meta postwalk-with-meta]]
            [y0.status :refer [ok let-s]]))

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

(defn- check-form [form]
  (let [[kw arg & others] form]
    (cond
      (not (empty? others)) (throw (Exception. (str kw " node should contain one element but has " (-> others count inc))))
      (not (string? arg)) (throw (Exception. (str kw " node should contain a single string. Found: " arg)))
      :else form)))

(defn symbolize [node ns identifier-keywords]
  (if (vector? node)
    (let [[kw name] node]
      (if
       (contains? identifier-keywords kw)
        (let [_ (check-form node)]
          [kw (symbol ns name)])
        node))
    node))

(defn extract-deps [node coll-atom kw]
  (when (vector? node)
    (let [[kw' module] node]
      (when (= kw' kw)
        (check-form node)
        (swap! coll-atom conj module))))
  node)

(defn convert-int-node [node]
  (if (vector? node)
    (let [[kw val] node]
      (if (= kw :int)
        (let [_ (check-form node)]
          [kw (java.lang.Integer/parseInt val)])
        node))
    node))

(defn convert-float-node [node]
  (if (vector? node)
    (let [[kw val] node]
      (if (= kw :float)
        (let [_ (check-form node)]
          [kw (java.lang.Double/parseDouble val)])
        node))
    node))

(defn- wrap-parse [func & args]
  (try
    (ok (apply func args))
    (catch Exception e {:err [(.getMessage e)]})))

(defn instaparser [lang grammar id-kws dep-kw]
  (fn [module path text]
    (let-s [parser (ok (instaparse-grammar grammar))
            parse-tree (wrap-parse parser text)
            statements (ok (drop 1 parse-tree))
            deps-atom (ok (atom nil))
            statements (ok (vec (postwalk-with-meta
                                 #(-> %
                                      (symbolize module id-kws)
                                      (extract-deps deps-atom dep-kw)
                                      convert-int-node
                                      convert-float-node) statements)))
            deps (ok (vec (for [dep @deps-atom]
                            {:lang lang
                             :name dep})))]
           (ok [statements deps]))))