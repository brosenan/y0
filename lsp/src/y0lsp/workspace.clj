(ns y0lsp.workspace
  (:require [y0.polyglot-loader :refer [module-id]]
            [loom.graph :refer [digraph add-nodes add-edges]]))

(declare add-module)

(defn new-workspace []
  {:ms {}
   :mg (digraph)})

(defn- add-dep [{:keys [ms mg]} m1 m2]
  (let [mid1 (module-id m1)
        mid2 (module-id m2)
        mg (add-edges mg [mid1 mid2])]
    {:ms ms :mg mg}))

(defn- add-deps [ws m load]
  (loop [deps (:deps m)
          ws ws]
     (if (empty? deps)
       ws
       (let [[dep & deps] deps]
         (recur deps (-> ws
                         (add-module dep load)
                         (add-dep dep m)))))))

(defn add-module [ws m load]
  (let [m (load m)
        ws (add-deps ws m load)
        mid (module-id m)]
   {:ms (assoc (:ms ws) mid m)
    :mg (add-nodes (:mg ws) mid)}))