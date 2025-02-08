(ns y0lsp.workspace
  (:require
   [clojure.set :as set]
   [loom.alg :refer [bf-traverse topsort]]
   [loom.graph :refer [add-edges add-nodes digraph predecessors remove-edges
                       subgraph transpose]]
   [y0.polyglot-loader :refer [module-id]]))

(declare add-module)

(defn new-workspace [load-fn eval-fn]
  {:ms {}
   :mg (digraph)
   :load-module load-fn
   :eval-module eval-fn})

(defn- update-predecessors [g mid deps]
  (let [old-deps (predecessors g mid)
        new-deps (set/difference deps old-deps)
        rem-deps (set/difference old-deps deps)
        g (apply remove-edges g (for [d rem-deps]
                                  [d mid]))
        g (apply add-edges g (for [d new-deps]
                               [d mid]))]
    g))

(defn- add-modules [ws mids]
  (loop [mids mids
         ws ws]
    (if (empty? mids)
      ws
      (let [[mid & mids] mids
            ws (add-module ws {:path mid})]
        (recur mids ws)))))

(defn- loaded? [{:keys [ms]} m]
  (let [mid (module-id m)]
    (contains? ms mid)))

(defn add-module [{:keys [load-module] :as ws} m]
  (if (loaded? ws m)
    ws
    (let [m (load-module m)
          deps (set (:deps m))
          ws (update ws :mg update-predecessors (module-id m) deps)
          ws (add-modules ws deps)
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

(defn eval-with-deps [{:keys [mg ms eval-module] :as ws} mid]
  (let [mg' (transpose mg)
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

(defn- update-graph-dependencies [{:keys [ms] :as ws} mid]
  (let [deps (-> ms (get mid) :deps set)]
    (-> ws
        (update :mg update-predecessors mid deps)
        (add-modules deps))))

;; TODO: Optimize so that if no new dependencies are introduced, evaluation
;; starts with :pre-ps.
(defn update-module [{:keys [load-module] :as ws} m]
  (let [mid (module-id m)]
    (if (-> ws :ms (contains? mid))
      (-> ws
          (invalidate-module mid)
          (update :ms assoc mid (load-module m))
          (update-graph-dependencies mid)
          (eval-with-deps mid))
      (-> ws
          (add-module {:path mid})
          (eval-with-deps mid)))))
