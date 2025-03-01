(ns y0lsp.node-nav)

(defn get-matches [node]
  (let [{:keys [matches]} (-> node meta)]
    (if (instance? clojure.lang.Atom matches)
      @matches
      {})))

(defn get-defs [node]
  (->> node
       get-matches
       (map (fn [[pred {:keys [def]}]]
              [pred def]))
       (filter (fn [[_pred def]]
                 (some? def)))
       (into {})))

(defn get-all-defs [node]
  (-> node get-defs vals))

(defn get-refs [node]
  (let [{:keys [refs]} (-> node meta)]
    (if (instance? clojure.lang.Atom refs)
      @refs
      [])))