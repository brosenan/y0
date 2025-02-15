(ns y0lsp.addons.init 
  (:require
   [lsp4clj.server :as server]
   [y0lsp.addon-utils :refer [add-req-handler register-addon]]
   [y0lsp.server :refer [register-notification register-req]]))

(register-req "initialize" any?)
(register-notification "y0lsp/initialized")
(register-addon "init"
                (comp
                 (->> (fn [{:keys [server-capabilities
                                   client-capabilities] :as ctx}
                           {:keys [capabilities] :as req}]
                        (reset! client-capabilities  capabilities)
                        (server/receive-notification "y0lsp/initialized" ctx req)
                        {:capabilities server-capabilities
                         :server-info {:name "y0lsp" :version y0lsp.server/version}})
                      (add-req-handler "initialize"))
                 #(assoc % :client-capabilities (atom nil))))