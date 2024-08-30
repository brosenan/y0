(ns y0.polyglot-loader
  (:require [y0.status :refer [ok ->s let-s]]))

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
