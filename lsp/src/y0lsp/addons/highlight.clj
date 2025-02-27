(ns y0lsp.addons.highlight
  (:require
   [lsp4clj.coercer :as coercer]
   [y0lsp.addon-utils :refer [add-node-and-lss-to-doc-pos add-req-handler
                              merge-server-capabilities register-addon]]
   [y0lsp.location-utils :refer [node-location]]
   [y0lsp.server :refer [register-req]]))

(defn- get-defs [node]
  (let [matches (-> node meta :matches)]
    (if (nil? matches)
      []
      (->> @matches
           (map (fn [[_pred {:keys [def]}]]
                  def))
           (filter #(not (nil? %)))))))

(defn- get-refs [node]
  (let [refs (-> node meta :refs)]
    (if (nil? refs)
      []
      @refs)))

(register-req "textDocument/documentHighlight" ::coercer/document-highlights)
(register-addon "highlight"
                (-> {:document-highlight-provider true}
                    merge-server-capabilities)
                (->> (fn [_ctx {:keys [node]}]
                       (let [defs (get-defs node)
                             refs (->> defs (mapcat get-refs))]
                         (for [node (concat defs refs)]
                           (let [{:keys [range]} (node-location node)]
                             {:range range}))))
                     add-node-and-lss-to-doc-pos
                     (add-req-handler "textDocument/documentHighlight")))
