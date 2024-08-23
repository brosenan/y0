(ns y0.modules
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.set :refer [union difference]]
            [edamame.core :as e :refer [parse-string-all]]
            [y0.core :refer [y0-symbols]]
            [y0.term-utils :refer [postwalk-with-meta]]))

(defn convert-ns [expr ns-map refer-map]
  (postwalk-with-meta
   (fn [expr] (if (symbol? expr)
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
                expr))
   expr))


(defn module-paths [module-name y0-path]
  (let [rel-path (str/split module-name #"[.]")
        depth (-> rel-path count dec)
        rel-path (update rel-path depth #(str % ".y0"))]
    (for [path y0-path]
      (apply io/file path rel-path))))

;; Reads a module to a string.
(defn read-module [module-name y0-path]
  (let [paths (module-paths module-name y0-path)
        existing (->> paths (filter #(.exists %)))]
    (if (empty? existing)
      (throw (Exception. (str "Cannot find module " module-name " in paths " (list y0-path))))
      (let [path (first existing)]
        [(slurp path) path]))))

(defn fold- [[m1 n1 r1] [m2 n2 r2]]
  [(concat m1 m2) (merge n1 n2) (merge r1 r2)])

(defn- handle-directive [args]
  (let [[name & {:keys [as refer]}] args
        name (str name)]
    [[name]
     (if (nil? as)
       {}
       {(str as) name})
     (if (nil? refer)
       {}
       (->> refer
            (map (fn [sym] [(str sym) name]))
            (into {})))]))

(defn- handle-require [vecs]
  (loop [vecs vecs
         res [[] {} {}]]
    (if (empty? vecs)
      res
      (let [[args & vecs] vecs]
        (recur vecs
               (fold- res
                      (handle-directive args)))))))

(defn parse-ns-decl [[_ns module-name & directives]]
  (let [module-name (str module-name)
        fold (fn [[m1 n1 r1] [m2 n2 r2]]
               [(concat m1 m2) (merge n1 n2) (merge r1 r2)])]
    (->> (for [[directive & args] directives]
           (cond
             (= directive ':require) (handle-require args)))
         (reduce fold [[] {nil module-name} {}]))))

(defn- convert-location [loc]
  (let [{:keys [col row end-col end-row]} loc]
    {:start (+ (* row 1000000) col)
     :end (+ (* end-row 1000000) end-col)}))

(defn load-single-module [module-name y0-path]
  (let [[module-text module-path] (read-module module-name y0-path)
        [ns-decl & statements] (parse-string-all module-text {:postprocess (fn [m]
                                                                             (if (instance? clojure.lang.IObj (:obj m))
                                                                               (with-meta (:obj m) (-> (convert-location (:loc m))
                                                                                                       (assoc :path module-path)))
                                                                               (:obj m)))})
        [module-list ns-map refer-map] (parse-ns-decl ns-decl)
        refer-map (merge refer-map (->> (for [sym y0-symbols]
                                          [(name sym) "y0.core"])
                                        (into {})))]
    [(for [statement statements]
       (convert-ns statement ns-map refer-map))
     module-list]))

(defn load-all-modules [modules-to-load y0-path]
  (loop [modules-to-load modules-to-load
         loaded {}]
    (if (empty? modules-to-load)
      loaded
      (let [newly-loaded (->> (for [module modules-to-load]
                                [module (load-single-module module y0-path)])
                              (into {}))
            new-deps (difference (set (apply concat (for [[_module [_statements deps]] newly-loaded]
                                                      deps)))
                                 (set (keys loaded)))]
        (recur new-deps (merge loaded newly-loaded))))))

(defn- remove-all-keys [m keys]
  (loop [m m
         keys keys]
    (if (empty? keys)
      m
      (recur (dissoc m (first keys))
             (rest keys)))))

(defn sort-statements-by-deps [loaded]
  (loop [statements []
         remaining loaded
         added #{}]
    (if (empty? remaining)
      statements
      (let [available (for [[module [_statements deps]] remaining
                            :when (empty? (difference (set deps) added))]
                        module)
            new-statements (for [module available
                                 statement (-> remaining (get module) first)]
                             statement)]
        (recur (concat statements new-statements)
               (remove-all-keys remaining available)
               (union added (set available)))))))

(defn load-with-dependencies [modules-to-load y0-path]
  (let [module-map (load-all-modules modules-to-load y0-path)]
    [(sort-statements-by-deps module-map) (set (keys module-map))]))