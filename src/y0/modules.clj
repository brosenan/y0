(ns y0.modules
  (:require [clojure.walk :as walk]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [edamame.core :as e :refer [parse-string-all]]))

(def y0-symbols ["<-" "..." "test" "clj-step" "return" "continue"])

(defn postwalk-with-meta [f x]
  (let [m (meta x)
        x' (cond
             (vector? x) (->> x (map #(postwalk-with-meta f %)) vec)
             :else x)
        y (f x')]
    (if (instance? clojure.lang.Obj y)
      (with-meta y m)
      y)))

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
      [(-> existing first slurp) existing])))

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

(defn load-single-module [module-name y0-path]
  (let [[module-text module-path] (read-module module-name y0-path)
        [ns-decl & statements] (parse-string-all module-text {:postprocess (fn [m]
                                                                             (if (instance? clojure.lang.IObj (:obj m))
                                                                               (with-meta (:obj m) (-> (:loc m)
                                                                                                       (assoc :path module-path)))
                                                                               (:obj m)))})
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