(ns y0.polyglot-loader
  (:require [y0.status :refer [ok ->s let-s]]
            [clojure.set :refer [union]]))

(defn- slurp-text [{:keys [path] :as m}]
  (ok m assoc :text (slurp path)))

(defn load-module [module lang-map]
  (let [{:keys [parse resolve]} (get lang-map (:lang module))]
    (cond
      (contains? module :statements) (ok module)
      (contains? module :text) (parse module)
      (contains? module :path) (->s (slurp-text module) (recur lang-map))
      (contains? module :name) (->s (resolve module) (recur lang-map)))))

(defn module-id [{:keys [lang name]}]
  (str lang ":" name))

(defn load-with-deps [m lang-map]
  (loop [ms [m]
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
  (->> (for [[mid m] mstore
             dep (:deps m)]
         {(module-id dep) #{mid}})
       (reduce (partial merge-with union))))

(defn mstore-toposort [mstore]
  (let [refs (mstore-refs mstore)
        srcs (mstore-sources mstore)]
    (loop [sorted (vec srcs)
           seen srcs
           queue (seq srcs)]
      (if (empty? queue)
        sorted
        (let [[mid & queue] queue
              queue (concat queue (seq (refs mid)))]
          (if (contains? seen mid)
            (recur sorted seen queue)
            (recur (conj sorted mid) (conj seen mid) queue)))))))

(defn eval-mstore [mstore eval-func]
  (loop [keys (mstore-toposort mstore)
         mstore mstore
         ps {}]
    (if (empty? keys)
      (ok mstore)
      (let-s [[key & keys] (ok keys)
              m (ok mstore get key)
              ps (eval-func ps (:statements m))
              m (ok m assoc :predstore ps)
              mstore (ok mstore assoc key m)]
             (recur keys mstore ps)))))