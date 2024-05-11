(ns y0.modules
  (:require [clojure.edn :as edn]
            [clojure.walk :as walk]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(def y0-symbols ["<-" "..." "test" "clj-step" "return" "continue"])

(defn convert-ns [expr ns-map refer-map]
  (walk/postwalk (fn [expr] (if (symbol? expr)
                              (let [meta (meta expr)
                                    orig-ns (namespace expr)
                                    new-ns (if (and (empty? orig-ns)
                                                    (refer-map (name expr)))
                                             (refer-map (name expr))
                                             (if-let [new-ns (ns-map orig-ns)]
                                               new-ns
                                               (throw (Exception. (str "Undefined namespace: " orig-ns)))))]
                                (-> (symbol new-ns (name expr))
                                    (with-meta meta)))
                              expr)) expr))


(defn module-paths [module-name y0-path]
  (let [rel-path (str/split module-name #"[.]")
        depth (-> rel-path count dec)
        rel-path (update rel-path depth #(str % ".mu"))]
    (for [path y0-path]
      (apply io/file path rel-path))))

;; Reads a module to a string.
(defn read-module [module-name y0-path]
  (let [paths (module-paths module-name y0-path)
        existing (->> paths (filter #(.exists %)))]
    (if (empty? existing)
      (throw (Exception. (str "Cannot find module " module-name " in paths " (list y0-path))))
      (-> existing first slurp))))

(defn- handle-directive [directive args module-name]
  (when (< (count args) 2)
    (throw (Exception. (str "A " directive " directive in module " module-name " requires at least two arguments. " (count args) " given."))))
  (when (> (count args) 3)
    (throw (Exception. (str "A " directive " directive in module " module-name " requires at most three arguments. " (count args) " given."))))
  (let [[name alias & others] args
        name (str name)
        alias (str alias)]
    [[name] {alias name} (if (= (count others) 1)
                           (->> others first
                                (map (fn [sym] [(str sym) name]))
                                (into {}))
                           {})]))

(defn- handle-require [args module-name]
  (handle-directive "require" args module-name))

(defn- handle-use [args module-name]
  (let [[_modules ns-map refer-map] (handle-directive "use" args module-name)]
    [[] ns-map refer-map]))

(defn parse-ns-decl [[_ns module-name & directives]]
  (let [module-name (str module-name)
        fold (fn [[m1 n1 r1] [m2 n2 r2]]
               [(concat m1 m2) (merge n1 n2) (merge r1 r2)])]
    (->> (for [[directive & args] directives]
           (cond
             (= directive 'require) (handle-require args module-name)
             (= directive 'use) (handle-use args module-name)))
         (reduce fold [[] {nil module-name} {}]))))

(defn load-single-module [module-name y0-path]
  (let [module-text (read-module module-name y0-path)
        [ns-decl & statements] (edn/read-string (str "(" module-text ")"))
        [module-list ns-map refer-map] (parse-ns-decl ns-decl)
        refer-map (merge refer-map (->> (for [sym y0-symbols]
                                          [sym "y0"])
                                        (into {})))]
    [(for [statement statements]
       (convert-ns statement ns-map refer-map))
     module-list]))

(defn load-with-dependencies [module-name y0-path]
  (loop [modules-to-load [module-name]
         statements []
         modules-loaded #{}]
    (if (empty? modules-to-load)
      [statements modules-loaded]
      (if (modules-loaded (first modules-to-load))
        (recur (rest modules-to-load) statements modules-loaded)
        (let [module-name (first modules-to-load)
              modules-to-load (rest modules-to-load)
              [module-statements module-deps] (load-single-module module-name y0-path)]
          (recur (concat module-deps modules-to-load)
                 (concat module-statements statements)
                 (conj modules-loaded module-name)))))))