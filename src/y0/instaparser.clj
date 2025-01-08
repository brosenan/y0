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

(defn symbolize
  ([node ns identifier-keywords]
   (if (keyword? identifier-keywords)
     (symbolize node ns #{identifier-keywords} true)
     (symbolize node ns identifier-keywords false)))
  ([node ns identifier-keywords extract-sym]
   (if (vector? node)
     (let [[kw name] node]
       (if
        (contains? identifier-keywords kw)
         (let [_ (check-form node)]
           (if extract-sym
             (with-meta (symbol ns name) (meta node))
             [kw (symbol ns name)]))
         node))
     node)))

(defn extract-deps
  ([node coll-atom kw ns]
   (extract-deps node coll-atom kw ns identity))
  ([node coll-atom kw ns resolve]
   (if (vector? node)
     (let [[kw' module] node]
       (if (= kw' kw)
         (let [path (resolve module)]
           (check-form node)
           (swap! coll-atom conj path)
           [kw (symbol ns path)])
         node))
     node)))

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

(defn instaparser [lang grammar id-kws dep-kw extra-deps]
  (fn parse
    ([_module path text]
     (parse _module path text identity))
    ([_module path text resolve]
     (let-s [parser (ok (instaparse-grammar grammar))
             parse-tree (wrap-parse parser text)]
            (let [statements (drop 1 parse-tree)
                  deps-atom (atom nil)
                  statements (add-locations statements path)
                  statements (vec (postwalk-with-meta
                                   #(-> %
                                        (symbolize path id-kws)
                                        (extract-deps deps-atom dep-kw
                                                      path resolve)
                                        convert-int-node
                                        convert-float-node) statements))
                  deps (-> @deps-atom
                           (concat extra-deps)
                           vec)]
              (ok [statements deps]))))))