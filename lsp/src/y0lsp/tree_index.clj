(ns y0lsp.tree-index)

(defn relate-node-to-pos [node pos]
  (let [{:keys [start end]} (meta node)]
    (cond (<= end pos) :before
          (> start pos) :after
          :else :within)))