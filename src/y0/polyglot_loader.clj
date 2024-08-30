(ns y0.polyglot-loader)

(defn- slurp-text [{:keys [path] :as m}]
  (assoc m :text (slurp path)))

(defn load-module [module lang-map]
  (let [{:keys [parse resolve]} (get lang-map (:lang module))]
    (cond
      (contains? module :statements) module
      (contains? module :text) (parse module)
      (contains? module :path) (recur (slurp-text module) lang-map)
      (contains? module :name) (recur (resolve module) lang-map))))
