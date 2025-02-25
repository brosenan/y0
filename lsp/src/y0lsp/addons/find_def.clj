(ns y0lsp.addons.find-def
  (:require
   [y0lsp.addon-utils :refer [add-node-and-lss-to-doc-pos add-req-handler
                              merge-server-capabilities register-addon]]
   [y0lsp.server :refer [register-req]]
   [y0lsp.location-utils :refer [node-location]]))

(register-req "textDocument/definition" any?)
(register-addon "find-def"
                (->> {:definition-provider true}
                     merge-server-capabilities)
                (->> (fn [_ctx {:keys [node]}]
                       (let [matches-atom (-> node meta :matches)
                             d (if (nil? matches-atom)
                                 nil
                                 (->> (for [[_pred {:keys [def]}] @matches-atom
                                            :when (not (nil? def))]
                                        def)
                                      first))]
                         (if (nil? d)
                           nil
                           (node-location d))))
                     add-node-and-lss-to-doc-pos
                     (add-req-handler "textDocument/definition")))