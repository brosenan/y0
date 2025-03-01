(ns y0lsp.addons.find-def
  (:require
   [clojure.spec.alpha :as s]
   [lsp4clj.coercer :as coercer]
   [y0lsp.addon-utils :refer [add-node-and-lss-to-doc-pos add-req-handler
                              merge-server-capabilities register-addon]]
   [y0lsp.location-utils :refer [node-location]]
   [y0lsp.node-nav :refer [get-all-defs]]
   [y0lsp.server :refer [register-req]]))

(register-req "textDocument/definition" (s/nilable ::coercer/location))
(register-addon "find-def"
                (->> {:definition-provider true}
                     merge-server-capabilities)
                (->> (fn [_ctx {:keys [node]}]
                       (let [d (-> node get-all-defs first)]
                         (if (nil? d)
                           nil
                           (node-location d))))
                     add-node-and-lss-to-doc-pos
                     (add-req-handler "textDocument/definition")))