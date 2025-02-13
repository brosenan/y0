(ns y0lsp.addons.init 
  (:require
   [y0lsp.addon-utils :refer [add-req-handler register-addon]]
   [y0lsp.server :refer [register-req]]))

(register-req "initialize" any?)
(register-addon "init"
                (comp
                 (->> (fn [{:keys [server-capabilities
                                   client-capabilities]}
                           {:keys [capabilities]}]
                        (reset! client-capabilities  capabilities)
                        {:capabilities server-capabilities
                         :server-info {:name "y0lsp" :version y0lsp.server/version}})
                      (add-req-handler "initialize"))
                 #(assoc % :client-capabilities (atom nil))))