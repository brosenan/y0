(ns y0.to-html
  (:require
   [clojure.string :as str]
   [y0.location-util :refer [drop-up-to extract-location take-span]]
   [y0.term-utils :refer [postwalk-meta]]))

(defn- annotate-module-meta [{:keys [matches]} name id]
  (when (some? matches)
    (swap! matches assoc :html {:module name
                              :id (id)})))

(defn annotate-module-nodes [ms]
  (let [last-id (atom 0)
        id #(swap! last-id inc)]
    (doall (for [[_key {:keys [name statements]}] ms]
             (postwalk-meta #(annotate-module-meta % name id) statements)))))

(defn module-name-to-html-file-name [name]
  (-> name
      (str/replace "/" "-")
      (str ".html")))

(defn node-uri [node]
  (let [{:keys [matches]} (meta node)]
    (if (nil? matches)
      nil
      (let [{:keys [module id]} (:html @matches)]
        (str (module-name-to-html-file-name module) "#" id)))))

(defn- take-up-to [lines pos]
  (if (some? pos)
    (take-span lines (- pos 1000000))
    lines))

(defn- lines-to-str [lines]
  (str/join "\n" lines))

(defn- tree-and-lines-to-hiccup [lines nodes {:keys [start end]} annotate]
  (loop [nodes nodes
         output []
         pos start]
    (if (empty? nodes)
      (concat output [(-> lines
                          (take-up-to end)
                          (drop-up-to pos)
                          lines-to-str )])
      (let [[node & nodes] nodes
            [elem attr] (annotate node)]
        (if (instance? clojure.lang.IObj node)
          (recur nodes (conj output
                             (lines-to-str (-> lines
                                               (take-up-to (-> node meta :start))
                                               (drop-up-to pos)))
                             (if (sequential? node)
                               (let [[_ & args] node]
                                 (vec
                                  (concat [elem attr]
                                          (tree-and-lines-to-hiccup lines args (meta node) annotate))))
                               [elem attr (lines-to-str
                                           (extract-location lines (meta node)))]))
                 (-> node meta :end))
          (recur nodes output pos))))))

(defn tree-to-hiccup [{:keys [text statements]} annotate]
  (let [lines (str/split text #"\n")]
    (tree-and-lines-to-hiccup lines statements {:start 0} annotate)))

(defn- assoc-if [m k v]
  (if (seq v)
    (assoc m k v)
    m))

(defn annotate-node [node]
  (let [{:keys [matches]} (meta node)
        matches (if (some? matches)
                  @matches
                  {})
        {:keys [html]} matches
        {:keys [id]} html
        attrs (-> {}
                  (assoc-if :id (str id))
                  (assoc-if :class 
                            (str/join " " (->> matches keys
                                               (filter symbol?) (map name)))))]
    [:span attrs]))