(ns y0lsp.addons.hover
  (:require
   [clojure.spec.alpha :as s]
   [lsp4clj.coercer :as coercer]
   [y0lsp.addon-utils :refer [add-node-and-lss-to-doc-pos add-req-handler
                              bind-stringify-expr merge-server-capabilities
                              register-addon]]
   [y0lsp.server :refer [register-req]]))

(register-req "textDocument/hover" (s/nilable ::coercer/hover))
(register-addon "hover"
                (->> {:hover-provider true}
                     merge-server-capabilities)
                (->> (fn [{:keys [client-capabilities]} {:keys [lss]}]
                       (let [text (lss :hover)
                             md-allowed (-> @client-capabilities
                                            :text-document
                                            :hover
                                            :content-format
                                            set
                                            (contains? "markdown"))
                             md (if md-allowed
                                  (lss :hover-md)
                                  nil)
                             contents (if (nil? md) text md)]
                         (if (nil? contents)
                           nil
                           {:contents [contents]})))
                     add-node-and-lss-to-doc-pos
                     bind-stringify-expr
                     (add-req-handler "textDocument/hover")))