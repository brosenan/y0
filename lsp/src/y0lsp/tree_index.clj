(ns y0lsp.tree-index)

(defn relate-node-to-pos [node pos]
  (let [{:keys [start end]} (meta node)]
    (cond (nil? start) :dontknow
          (<= end pos) :before
          (> start pos) :after
          :else :within)))

(defn find-sub-node-at-pos [nodes pos]
  (loop [nodes nodes
         prev nil]
    (if (empty? nodes)
      nil
      (let [[node & nodes] nodes
            rel (relate-node-to-pos node pos)]
        (cond
          (= rel :before) (recur nodes prev)
          (= rel :dontknow) (recur nodes prev)
          (= rel :after) prev
          (sequential? node) (recur node node)
          :else node)))))