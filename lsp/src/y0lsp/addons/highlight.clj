(ns y0lsp.addons.highlight
  (:require
   [lsp4clj.coercer :as coercer]
   [y0lsp.addon-utils :refer [add-node-and-lss-to-doc-pos add-req-handler
                              merge-server-capabilities register-addon]]
   [y0lsp.location-utils :refer [node-location]]
   [y0lsp.node-nav :refer [get-all-defs get-refs]]
   [y0lsp.server :refer [register-req]]))

(register-req "textDocument/documentHighlight" ::coercer/document-highlights)
(register-addon "highlight"
                (-> {:document-highlight-provider true}
                    merge-server-capabilities)
                (->> (fn [_ctx {:keys [text-document node]}]
                       (let [defs (get-all-defs node)
                             refs (->> defs (mapcat get-refs))]
                         (->> (concat defs refs)
                              (map node-location)
                              (filter #(= (:uri %) (:uri text-document)))
                              (map #(dissoc % :uri)))))
                     add-node-and-lss-to-doc-pos
                     (add-req-handler "textDocument/documentHighlight")))
