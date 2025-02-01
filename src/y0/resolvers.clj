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
  (.exists file))

(defn prefix-list-resolver [paths rel-resolve]
  (fn [module]
    (let-s [suffix (rel-resolve module)]
      (loop [paths paths]
        (if (empty? paths)
          {:err ["Could not find path for module" module]}
          (let [[path & paths] paths
                candidate (io/file path suffix)]
            (println ">" candidate)
            (if (exists? candidate)
              (-> candidate .getAbsolutePath java.io.File. ok)
              (recur paths))))))))

(defn getenv [env]
  (if-let [val (java.lang.System/getenv env)]
    val
    (throw (Exception. (str "Missing expected environment variable: " env)))))

(defn- lazy-val [thunk]
  (let [a (atom nil)]
    (fn []
      (when (nil? @a)
        (reset! a (thunk)))
      @a)))

(defn path-prefixes-from-env [env]
  (let [s (lazy-val #(seq (str/split (getenv env) #"[:]")))]
    (reify clojure.lang.ISeq
      (seq [_this] (s))
      (next [_this] (rest (s)))
      (first [_this] (first (s)))
      (more [this] (next this)))))

(defn y0-resolver [y0-path]
  (prefix-list-resolver y0-path (qname-to-rel-path-resolver "y0")))