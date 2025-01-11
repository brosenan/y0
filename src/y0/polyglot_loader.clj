(ns y0.polyglot-loader
  (:require [clojure.set :refer [union]]
            [loom.graph :refer [digraph]]
            [loom.alg :refer [topsort]]
            [y0.status :refer [ok ->s let-s]]
            [y0.term-utils :refer [postwalk-meta]]))

(defn- slurp-text [{:keys [path] :as m} read-fn]
  (ok m assoc :text (read-fn path)))

(defn- decorate-tree [tree]
  (postwalk-meta (fn [m]
                   (merge m {:matches (atom {})
                             :refs (atom nil)})) tree))

(defn- find-lang [lang-map path]
  (let [lang (first (for [[lang {:keys [match]}] lang-map
                          :when (match path)]
                      lang))]
    (if (seq lang)
      {:ok lang}
      {:err ["Could not find a language to match" path]})))

(defn load-module [module lang-map]
  (let [{:keys [lang]} module]
    (if (contains? lang-map lang)
      (let [lang-def (get lang-map lang)
            {:keys [parse read resolve decorate]} lang-def]
        (cond
          (contains? module :statements) (ok module)
          (contains? module :text) (let-s [[statements deps] (parse (:name module)
                                                                    (:path module)
                                                                    (:text module))
                                           statements (ok (if decorate
                                                            (decorate-tree statements)
                                                            statements))]
                                          (ok (-> module
                                                  (assoc :statements statements)
                                                  (assoc :deps deps))))
          (contains? module :path) (->s (slurp-text module read) (recur lang-map))
          (contains? module :name) (let-s [path (resolve (:name module))
                                           module (ok module assoc :path path)]
                                          (recur module lang-map))))
      (let-s [lang (find-lang lang-map (:path module))]
             (recur (assoc module :lang lang) lang-map)))))

(defn module-id [{:keys [lang name]}]
  (str lang ":" name))

(defn- mark-as-main [ms]
  (map #(assoc % :is-main true) ms))

(defn load-with-deps [ms lang-map]
  (loop [ms (mark-as-main ms)
         mstore {}]
    (if (empty? ms)
      (ok mstore)
      (let [[m & ms] ms]
        (if (contains? mstore (module-id m))
          (recur ms mstore)
          (let-s [m (load-module m lang-map)]
                 (recur (concat ms (:deps m))
                        (assoc mstore (module-id m) m))))))))

(defn mstore-sources [mstore]
  (set (for [[mid m] mstore
             :when (empty? (:deps m))]
         mid)))

(defn mstore-refs [mstore]
  (let [base (->> (for [[key _] mstore]
                    {key #{}})
                  (into {}))]
    (->> (for [[mid m] mstore
               dep (:deps m)]
           {(module-id dep) #{mid}})
         (reduce (partial merge-with union) base))))

(defn mstore-toposort [mstore]
  (let [refs (mstore-refs mstore)
        graph (digraph refs)]
    (topsort graph)))

(defn eval-mstore [mstore eval-func ps]
  (loop [keys (mstore-toposort mstore)
         mstore mstore
         ps ps]
    (if (empty? keys)
      (ok mstore)
      (let-s [[key & keys] (ok keys)
              m (ok mstore get key)
              ps (eval-func ps (:statements m) (:is-main m))
              m (ok m assoc :predstore ps)
              mstore (ok mstore assoc key m)]
             (recur keys mstore ps)))))