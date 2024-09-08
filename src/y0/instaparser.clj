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

(comment
  
  )