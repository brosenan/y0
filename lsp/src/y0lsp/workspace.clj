(ns y0lsp.workspace
  (:require [y0.polyglot-loader :refer [module-id]]
            [loom.graph :refer [digraph add-nodes add-edges transpose subgraph]]
            [loom.alg :refer [bf-traverse topsort]]))

(declare add-module)

(defn new-workspace [load-fn eval-fn]
  {:ms {}
   :mg (digraph)
   :load-module load-fn
   :eval-module eval-fn})

(defn- add-dep [{:keys [ms mg] :as ws} m1 m2]
  (let [mid1 (module-id m1)
        mid2 (module-id m2)
        mg (add-edges mg [mid1 mid2])]
    (-> ws
        (assoc :ms ms)
        (assoc :mg mg))))

(defn- add-deps [ws m]
  (loop [deps (:deps m)
          ws ws]
     (if (empty? deps)
       ws
       (let [[dep & deps] deps]
         (recur deps (-> ws
                         (add-module {:path dep})
                         (add-dep {:path dep} m)))))))

(defn- loaded? [{:keys [ms]} m]
  (let [mid (module-id m)]
    (contains? ms mid)))

(defn add-module [{:keys [load-module] :as ws} m]
  (if (loaded? ws m)
    ws
    (let [m (load-module m)
          ws (add-deps ws m)
          mid (module-id m)
          ms (assoc (:ms ws) mid m)
          mg (add-nodes (:mg ws) mid)]
      (-> ws
          (assoc :ms ms)
          (assoc :mg mg)))))

(defn- find-pivot [keys ms]
  (->> keys
       (filter #(-> ms (get %) (contains? :cache)))
       first))

(defn- update-refs [ms deps mid]
  (if (empty? deps)
    ms
    (let [[dep & deps] deps
          ms (update-in ms [dep :cache :refs] conj mid)]
      (recur ms deps mid))))

(defn eval-with-deps [{:keys [mg ms eval-module] :as ws} m]
  (let [mid (module-id m)
        mg' (transpose mg)
        rec-deps (bf-traverse mg' mid)
        pivot (find-pivot rec-deps ms)
        stop-set (-> ms (get pivot) :cache :all-deps)
        rec-deps (bf-traverse mg' mid
                              :when (fn [neighb _prev _depth]
                                      (not (contains? stop-set neighb))))
        rec-deps-graph (subgraph mg rec-deps)
        rec-deps-sorted (topsort rec-deps-graph)]
    (loop [mids rec-deps-sorted
           ms ms
           ps (-> ms (get pivot) :cache :ps)
           all-deps #{}]
      (if (empty? mids)
        (assoc ws :ms ms)
        (let [pre-ps ps
              [mid & mids] mids
              m (get ms mid)
              ps (eval-module ps m)
              all-deps (conj all-deps mid)
              ms (update ms (module-id m)
                         #(assoc % :cache {:pre-ps pre-ps
                                           :ps ps
                                           :all-deps all-deps
                                           :refs #{}}))
              ms (update-refs ms (seq all-deps) (module-id m))]
          (recur mids ms ps all-deps))))))

(defn- remove-cache-from [ws refs]
  (if (empty? refs)
    ws
    (let [[ref & refs] refs
          ws (update-in ws [:ms ref] dissoc :cache)]
      (recur ws refs))))

(defn invalidate-module [ws mid]
  (let [{:keys [refs]} (-> ws :ms (get mid) :cache)]
    (remove-cache-from ws (seq refs))))

;; TODO: Optimize so that if no new dependencies are introduced, evaluation
;; starts with :pre-ps.
(defn update-module [{:keys [load-module] :as ws} mid]
  (if (-> ws :ms (contains? mid))
    (-> ws
        (invalidate-module mid)
        (update-in [:ms mid] load-module)
        (eval-with-deps {:path mid}))
    (-> ws
        (add-module {:path mid})
        (eval-with-deps {:path mid}))))
