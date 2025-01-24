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

(defn nodes-after-pos [nodes pos]
  (loop [nodes nodes]
    (if (empty? nodes)
      nodes
      (let [[node & nodes'] nodes
            rel (relate-node-to-pos node pos)]
        (cond
          (= rel :before) (recur nodes')
          (= rel :dontknow) (recur nodes')
          (= rel :after) nodes
          (sequential? node) (recur (concat node nodes'))
          :else nodes)))))

(defn- nodes-before-pos-ref [nodes pos]
  (if (empty? nodes)
    nodes
    (let [[node & nodes'] nodes
          rel (relate-node-to-pos node pos)]
      (cond
        (= rel :after) (recur nodes' pos)
        (= rel :before) nodes
        (sequential? node) (recur (concat (reverse node) nodes') pos)
        (= rel :within) (recur nodes' pos)
        (= rel :dontknow) (recur nodes' pos)
        :else nodes))))

(defn nodes-before-pos [nodes pos]
  (-> nodes
      reverse
      (nodes-before-pos-ref pos)
      reverse))

(defn nodes-within-range [nodes start end]
  (-> nodes
      (nodes-after-pos start)
      (nodes-before-pos end)))