(ns y0.resolvers
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn qname-to-rel-path-resolver [ext]
  (fn [qname]
    (let [rel-path (str/split qname #"[.]")
          depth (-> rel-path count dec)
          rel-path (update rel-path depth #(str % "." ext))]
      (apply io/file rel-path))))