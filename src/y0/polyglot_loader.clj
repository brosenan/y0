(ns y0.polyglot-loader
  (:require [y0.status :refer [ok ->s]]))

(defn- slurp-text [{:keys [path] :as m}]
  (ok m assoc :text (slurp path)))

(defn load-module [module lang-map]
  (let [{:keys [parse resolve]} (get lang-map (:lang module))]
    (cond
      (contains? module :statements) (ok module)
      (contains? module :text) (parse module)
      (contains? module :path) (->s (ok module) (slurp-text) (recur lang-map))
      (contains? module :name) (->s (resolve module) (recur lang-map)))))
