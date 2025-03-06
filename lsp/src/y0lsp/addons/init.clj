(ns y0lsp.addons.init 
  (:require
   [lsp4clj.server :as server]
   [y0lsp.addon-utils :refer [add-req-handler deep-merge register-addon]]
   [y0lsp.server :refer [register-notification register-req]]))

(register-req "initialize" any?)
(register-notification "initialized")
(register-notification "y0lsp/initialized")
(register-addon "init"
                (->> (fn [{:keys [server-capabilities
                                  client-capabilities
                                  capability-providers] :as ctx}
                          {:keys [capabilities] :as req}]
                       (reset! client-capabilities  capabilities)
                       (server/receive-notification "y0lsp/initialized" ctx req)
                       (let [server-capabilities (reduce (fn [c1 f]
                                                           (deep-merge c1 (f capabilities)))
                                                         server-capabilities
                                                         capability-providers)]
                         {:capabilities server-capabilities
                          :server-info {:name "y0lsp" :version y0lsp.server/version}}))
                     (add-req-handler "initialize"))
                #(assoc % :client-capabilities (atom nil)))