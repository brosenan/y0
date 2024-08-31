(ns y0.resolvers
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [y0.status :refer [ok let-s]]))

(defn qname-to-rel-path-resolver [ext]
  (fn [qname]
    (let [rel-path (str/split qname #"[.]")
          depth (-> rel-path count dec)
          rel-path (update rel-path depth #(str % "." ext))]
      (ok (apply io/file rel-path)))))

(defn exists? [file]
  (.exist file))

(defn prefix-list-resolver [paths rel-resolve]
  (fn [module]
    (let-s [suffix (rel-resolve module)]
      (loop [paths paths]
        (if (empty? paths)
          {:err ["Could not find path for module" module]}
          (let [[path & paths] paths
                candidate (io/file path suffix)]
            (if (exists? candidate)
              (ok candidate)
              (recur paths))))))))