(ns y0lsp.workspace
  (:require [y0.polyglot-loader :refer [module-id]]
            [loom.graph :refer [digraph add-nodes add-edges transpose subgraph]]
            [loom.alg :refer [bf-traverse topsort]]))

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

(defn- loaded? [{:keys [ms]} m]
  (let [mid (module-id m)]
    (contains? ms mid)))

(defn add-module [ws m load]
  (if (loaded? ws m)
    ws
    (let [m (load m)
          ws (add-deps ws m load)
          mid (module-id m)]
      {:ms (assoc (:ms ws) mid m)
       :mg (add-nodes (:mg ws) mid)})))

(defn eval-with-deps [{:keys [mg ms] :as ws} m eval-module]
  (let [mid (module-id m)
        mg' (transpose mg)
        rec-deps (bf-traverse mg' mid)
        rec-deps-graph (subgraph mg rec-deps)
        rec-deps-sorted (topsort rec-deps-graph)]
    (loop [mids rec-deps-sorted
           ms ms
           ps {}
           all-deps #{}]
      (if (empty? mids)
        (assoc ws :ms ms)
        (let [pre-ps ps
              [mid & mids] mids
              m (get ms mid)
              ps (eval-module ps m)
              all-deps (conj all-deps mid)
              ms (update ms (module-id m) #(assoc % :cache {:pre-ps pre-ps
                                                            :ps ps
                                                            :all-deps all-deps}))]
          (recur mids ms ps all-deps))))))