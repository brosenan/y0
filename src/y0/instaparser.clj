(ns y0.instaparser
  (:require [instaparse.core :as insta]
            [clojure.string :as str]))

(def layout-separator "--layout--")

(defn instaparse-grammar [grammar]
  (if (str/includes? grammar layout-separator)
    (let [index (str/index-of grammar layout-separator)
          layout-grammar (subs grammar (+ index (count layout-separator)))
          layout-parser (insta/parser layout-grammar)
          grammar (subs grammar 0 index)]
      (insta/parser grammar :auto-whitespace layout-parser))
    (insta/parser grammar)))