(ns y0.modules
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.set :refer [union difference]]
            [y0.edn-parser :refer [edn-parser]]
            [y0.core :refer [y0-root-refer-map]]
            [y0.status :refer [ok let-s]]))

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

(defn load-single-module [module-name y0-path]
  (let-s [[module-text module-path] (ok (read-module module-name y0-path))
          parser (ok (edn-parser y0-root-refer-map))
          [statements module-list] (parser module-name module-path module-text)
          module-list (ok (for [{:keys [name]} module-list]
                            name))]
         [statements module-list]))

(defn load-all-modules [modules-to-load y0-path]
  (loop [modules-to-load modules-to-load
         loaded {}]
    (if (empty? modules-to-load)
      loaded
      (let [newly-loaded (->> (for [module modules-to-load]
                                [module (load-single-module module y0-path)])
                              (into {}))
            new-deps (for [[_module [_statements deps]] newly-loaded
                           dep deps
                           :when (not (contains? loaded dep))]
                       dep)]
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